package br.com.geniusquest.service;

import br.com.geniusquest.dto.tema.*;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

@Service
public class TemaService {
    private final TemaRepository temas;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;

    public TemaService(TemaRepository temas, CategoriaRepository categorias, UsuarioRepository usuarios) {
        this.temas = temas;
        this.categorias = categorias;
        this.usuarios = usuarios;
    }

    @Transactional
    public TemaResponseDTO sugerir(TemaCadastroDTO dto, String email) {
        Usuario autor = usuario(email);
        Categoria categoria = categorias.findById(dto.categoriaId())
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
        if (categoria.getStatus() != StatusCategoria.APROVADA) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Categoria não está aprovada");
        }

        String titulo = dto.titulo().strip().replaceAll("\\s+", " ");
        String normalizado = normalizar(titulo);
        if (titulo.length() < 3) {
            throw new RegraTemasException(HttpStatus.BAD_REQUEST, "Título deve ter ao menos 3 caracteres");
        }
        if (temas.existsByCategoria_IdAndTituloNormalizadoAndStatusNot(
                categoria.getId(), normalizado, StatusTema.REMOVIDO)) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Já existe tema com esse título na categoria");
        }

        Tema tema = new Tema();
        tema.setAutor(autor);
        tema.setCategoria(categoria);
        tema.setTitulo(titulo);
        tema.setTituloNormalizado(normalizado);
        tema.setDescricao(dto.descricao() == null ? null : dto.descricao().strip());
        try {
            return response(temas.saveAndFlush(tema));
        } catch (DataIntegrityViolationException ex) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Já existe tema com esse título na categoria");
        }
    }

    @Transactional(readOnly = true)
    public List<TemaResponseDTO> listarAprovados() {
        return temas.findByStatusOrderByCriadoEmAsc(StatusTema.APROVADO).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<TemaResponseDTO> listarModeracao() {
        return temas.findAllByOrderByCriadoEmAsc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public TemaResponseDTO buscarVisivel(UUID id, String email, boolean admin) {
        Tema tema = tema(id);
        if (tema.getStatus() != StatusTema.APROVADO && !admin
                && !tema.getAutor().getEmail().equalsIgnoreCase(email)) {
            throw new RegraTemasException(HttpStatus.NOT_FOUND, "Tema não encontrado");
        }
        return response(tema);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TemaResponseDTO atualizar(UUID id, TemaAtualizacaoDTO dto) {
        Tema tema = tema(id);
        if (tema.getStatus() == StatusTema.REMOVIDO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Tema removido não pode ser alterado");
        }
        if (dto.titulo() != null) {
            String titulo = dto.titulo().strip().replaceAll("\\s+", " ");
            if (titulo.length() < 3) {
                throw new RegraTemasException(HttpStatus.BAD_REQUEST, "Título deve ter ao menos 3 caracteres");
            }
            String normalizado = normalizar(titulo);
            if (!normalizado.equals(tema.getTituloNormalizado())
                    && temas.existsByCategoria_IdAndTituloNormalizadoAndStatusNot(
                            tema.getCategoria().getId(), normalizado, StatusTema.REMOVIDO)) {
                throw new RegraTemasException(HttpStatus.CONFLICT, "Já existe tema com esse título na categoria");
            }
            tema.setTitulo(titulo);
            tema.setTituloNormalizado(normalizado);
        }
        if (dto.descricao() != null) tema.setDescricao(dto.descricao().strip());
        try {
            return response(temas.saveAndFlush(tema));
        } catch (DataIntegrityViolationException ex) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Já existe tema com esse título na categoria");
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TemaResponseDTO moderar(UUID id, StatusTema status) {
        Tema tema = tema(id);
        if (tema.getStatus() == StatusTema.REMOVIDO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Tema removido não pode ser reativado");
        }
        tema.setStatus(status);
        return response(temas.save(tema));
    }

    private Usuario usuario(String email) {
        Usuario autor = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.UNAUTHORIZED, "Conta não identificada"));
        if (!Boolean.TRUE.equals(autor.getAtivo())) {
            throw new RegraTemasException(HttpStatus.FORBIDDEN, "Conta inativa");
        }
        return autor;
    }

    private Tema tema(UUID id) {
        return temas.findById(id)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Tema não encontrado"));
    }

    private static String normalizar(String titulo) {
        return Normalizer.normalize(titulo, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private TemaResponseDTO response(Tema tema) {
        return new TemaResponseDTO(tema.getId(), tema.getCategoria().getId(),
                tema.getAutor().getId(), tema.getTitulo(), tema.getDescricao(),
                tema.getStatus(), tema.getCriadoEm(), tema.getAtualizadoEm());
    }
}
