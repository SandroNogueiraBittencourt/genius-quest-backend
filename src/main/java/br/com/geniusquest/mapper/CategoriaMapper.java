package br.com.geniusquest.mapper;

import org.springframework.stereotype.Component;

import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.Categoria;

@Component
public class CategoriaMapper {

    public CategoriaResponseDTO toResponse(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getStatus(),
                categoria.getCriadoEm(),
                categoria.getAtualizadoEm()
        );
    }

    public Categoria toEntity(CategoriaCadastroDTO dto) {
        Categoria categoria = new Categoria();

        categoria.setNome(dto.nome().trim());

        if (dto.descricao() != null) {
            categoria.setDescricao(dto.descricao().trim());
        }

        return categoria;
    }
}
