package br.com.geniusquest.mapper;

import org.springframework.stereotype.Component;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.entity.Categoria;

@Component
public class CategoriaMapper {

    public CategoriaDTO toDTO(Categoria categoria) {
        return new CategoriaDTO(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getStatus(),
                categoria.getCriadoEm(),
                categoria.getAtualizadoEm());
    }

    public Categoria toEntity(CategoriaDTO dto) {
        Categoria categoria = new Categoria();

        categoria.setNome(dto.nome());
        categoria.setDescricao(dto.descricao());

        return categoria;
    }

}
