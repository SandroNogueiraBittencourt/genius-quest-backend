package br.com.geniusquest.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geniusquest.dto.categoria.CategoriaAtualizacaoDTO;
import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaInvalidaException;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.mapper.CategoriaMapper;
import br.com.geniusquest.repository.CategoriaRepository;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final CategoriaMapper mapper;

    public CategoriaService(
            CategoriaRepository repository,
            CategoriaMapper mapper) {

        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public CategoriaResponseDTO cadastrar(CategoriaCadastroDTO dto, boolean administrador) {

        validarNome(dto.nome());
        validarDescricao(dto.descricao());
        String nome = dto.nome().trim();

        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new CategoriaJaCadastradaException();
        }

        Categoria categoria = mapper.toEntity(dto);
        categoria.setStatus(administrador ? StatusCategoria.APROVADA : StatusCategoria.PENDENTE);

        Categoria categoriaSalva = repository.save(categoria);

        return mapper.toResponse(categoriaSalva);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar() {

        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarAprovadas() {

        return repository
                .findByStatusOrderByNomeAsc(StatusCategoria.APROVADA)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarPendentes() {

        return repository
                .findByStatusOrderByNomeAsc(StatusCategoria.PENDENTE)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarRemovidas() {

        return repository
                .findByStatusOrderByNomeAsc(StatusCategoria.REMOVIDA)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponseDTO> listar(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponseDTO> listarAprovadas(Pageable pageable) {
        return repository.findByStatus(StatusCategoria.APROVADA, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponseDTO> listarPendentes(Pageable pageable) {
        return repository.findByStatus(StatusCategoria.PENDENTE, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponseDTO> listarRemovidas(Pageable pageable) {
        return repository.findByStatus(StatusCategoria.REMOVIDA, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoriaResponseDTO buscarPorId(UUID id) {

        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        return mapper.toResponse(categoria);
    }

    @Transactional
    public CategoriaResponseDTO atualizar(
            UUID id,
            CategoriaAtualizacaoDTO dto) {

        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        validarDescricao(dto.descricao());

        if (dto.nome() != null) {
            validarNome(dto.nome());

            String novoNome = dto.nome().trim();

            boolean mudouDeNome =
                    !novoNome.equalsIgnoreCase(categoria.getNome());

            if (mudouDeNome
                    && repository.existsByNomeIgnoreCase(novoNome)) {

                throw new CategoriaJaCadastradaException();
            }

            categoria.setNome(novoNome);
        }

        if (dto.descricao() != null) {
            categoria.setDescricao(dto.descricao().trim());
        }

        return mapper.toResponse(categoria);
    }

    @Transactional
    public CategoriaResponseDTO aprovar(UUID id) {

        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        categoria.setStatus(StatusCategoria.APROVADA);

        return mapper.toResponse(categoria);
    }

    @Transactional
    public CategoriaResponseDTO remover(UUID id) {

        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        categoria.setStatus(StatusCategoria.REMOVIDA);

        return mapper.toResponse(categoria);
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new CategoriaInvalidaException("O nome da categoria é obrigatório");
        }
        int tamanho = nome.trim().length();
        if (tamanho < 2 || tamanho > 80) {
            throw new CategoriaInvalidaException(
                    "O nome da categoria deve possuir entre 2 e 80 caracteres");
        }
    }

    private static void validarDescricao(String descricao) {
        if (descricao != null && descricao.trim().length() > 255) {
            throw new CategoriaInvalidaException(
                    "A descrição da categoria deve possuir no máximo 255 caracteres");
        }
    }
}
