package br.com.geniusquest.mapper;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;

public class CategoriaMapperTest {
    private final CategoriaMapper mapper = new CategoriaMapper();

    @Test
    void toEntityNaoDeveCopiarOStatusDoDto() {
        CategoriaDTO dto = new CategoriaDTO(
                null, "Tecnologia", "Descrição", StatusCategoria.APROVADA, null, null);

        Categoria categoria = mapper.toEntity(dto);

        assertNull(categoria.getStatus());
    }
}
