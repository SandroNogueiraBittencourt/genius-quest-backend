package br.com.geniusquest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.entity.Categoria;

class CategoriaMapperTest {

    private final CategoriaMapper mapper = new CategoriaMapper();

    @Test
    void deveConverterCadastroDtoParaEntidade() {

        CategoriaCadastroDTO dto =
                new CategoriaCadastroDTO(
                        "Tecnologia",
                        "Categoria de tecnologia"
                );

        Categoria categoria = mapper.toEntity(dto);

        assertEquals("Tecnologia", categoria.getNome());
        assertEquals(
                "Categoria de tecnologia",
                categoria.getDescricao()
        );

        assertNull(categoria.getId());
        assertNull(categoria.getStatus());
    }

    @Test
    void deveNormalizarEspacosNoCadastro() {

        CategoriaCadastroDTO dto =
                new CategoriaCadastroDTO(
                        "  Tecnologia  ",
                        "  Categoria de tecnologia  "
                );

        Categoria categoria = mapper.toEntity(dto);

        assertEquals("Tecnologia", categoria.getNome());
        assertEquals(
                "Categoria de tecnologia",
                categoria.getDescricao()
        );
    }
}
