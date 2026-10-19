package br.com.geniusquest.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geniusquest.dto.categoria.CategoriaAtualizacaoDTO;
import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.mapper.CategoriaMapper;
import br.com.geniusquest.repository.CategoriaRepository;

@Service 
public class CategoriaService {
    private final CategoriaRepository repository;
    private final CategoriaMapper mapper;

    public CategoriaService(
        CategoriaRepository repository,
        CategoriaMapper mapper

    ){
        this.repository = repository;
        this.mapper = mapper;

    }

    @Transactional 
    public CategoriaResponseDTO cadastrar(CategoriaCadastroDTO dto){
        String nome = dto.nome()
                .trim();

        if (repository.existsByNomeIgnoreCase(nome)) {
            
        }

        Categoria categoria = mapper.toEntity(dto);

        categoria.setNome(nome);

        Categoria categoriaSalva = repository.save(categoria);

        return mapper.toResponse(categoriaSalva);
    }

    @Transactional (readOnly = true)
    public List<CategoriaResponseDTO> listar(){
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional (readOnly = true)
    public List<CategoriaResponseDTO> listarAtivas(){
        return repository.findByAtivoTrueOrderByNomeAsc()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional (readOnly = true)
    public CategoriaResponseDTO buscarPorId(UUID id){
        Categoria categoria = repository.findById(id)
                .orElseThrow();

        return mapper.toResponse(categoria);
    }

    @Transactional 
    public CategoriaResponseDTO atualizar(UUID id, CategoriaAtualizacaoDTO dto){
        Categoria categoria = repository.findById(id)
                .orElseThrow();

        if (dto.nome() != null) {
        String novoNome = dto.nome().trim();

        boolean mudouDeNome = !novoNome.equalsIgnoreCase(categoria.getNome());
        if (mudouDeNome && repository.existsByNomeIgnoreCase(novoNome)) {
            
        }
        categoria.setNome(novoNome);
    }
        if (dto.descricao() != null) {
            categoria.setDescricao(
                dto.descricao().trim()
            );
        }

        if (dto.ativo() != null) {
        categoria.setAtivo(dto.ativo());
        }
        return mapper.toResponse(categoria);
    }

}
