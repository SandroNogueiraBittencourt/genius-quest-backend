package br.com.geniusquest.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.mapper.CategoriaMapper;
import br.com.geniusquest.repository.CategoriaRepository;

@Service 
public class CategoriaService {
    private final CategoriaRepository repository;
    private final CategoriaMapper mapper;

    public CategoriaService(CategoriaRepository repository, CategoriaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public CategoriaDTO cadastrar(CategoriaDTO dto) {
        String nome = dto.nome().trim();

        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new CategoriaJaCadastradaException();
        }

        Categoria categoria = mapper.toEntity(dto);
        categoria.setNome(nome);

        Categoria categoriaSalva = repository.save(categoria);

        return mapper.toDTO(categoriaSalva);
    }

    @Transactional (readOnly = true)
    public List<CategoriaDTO> listar() {
        return repository.findAll()
                .stream()
                .map(mapper::toDTO)
                .toList();
    }
    @Transactional(readOnly = true)
    public List<CategoriaDTO> listarAprovadas() {
        return repository.findByStatusOrderByNomeAsc(StatusCategoria.APROVADA)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaDTO> listarPendentes() {
        return repository.findByStatusOrderByNomeAsc(StatusCategoria.PENDENTE)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaDTO> listarRemovidas() {
        return repository.findByStatusOrderByNomeAsc(StatusCategoria.REMOVIDA)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaDTO buscarPorId(UUID id) {
        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        return mapper.toDTO(categoria);
    }

    @Transactional
    public CategoriaDTO atualizar(UUID id, CategoriaDTO dto) {
        Categoria categoria = repository.findById(id)
                .orElseThrow(CategoriaNaoEncontradaException::new);

        if (dto.nome() != null) {
            String novoNome = dto.nome().trim();

            boolean mudouDeNome = !novoNome.equalsIgnoreCase(categoria.getNome());
            if (mudouDeNome && repository.existsByNomeIgnoreCase(novoNome)) {
                throw new CategoriaJaCadastradaException();
            }
            categoria.setNome(novoNome);
        }

        if (dto.descricao() != null) {
            categoria.setDescricao(dto.descricao().trim());
        }

        if (dto.status() != null) {
            categoria.setStatus(dto.status());
        }

        return mapper.toDTO(categoria);
    }

}
