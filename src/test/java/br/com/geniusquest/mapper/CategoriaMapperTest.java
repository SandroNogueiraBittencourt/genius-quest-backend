package br.com.geniusquest.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;

class CategoriaMapperTest {

    private final CategoriaMapper mapper = new CategoriaMapper();

    @Test
    void aceitaCadastroSemDescricao() {
        var categoria = mapper.toEntity(new CategoriaCadastroDTO("Tecnologia", null));

        assertEquals("Tecnologia", categoria.getNome());
        assertNull(categoria.getDescricao());
    }

    @Test
    void preservaStatusEDatasNaResposta() {
        UUID id = UUID.randomUUID();
        Instant criada = Instant.parse("2026-10-01T12:00:00Z");
        Instant atualizada = Instant.parse("2026-10-02T14:00:00Z");
        var categoria = new Categoria(id, "Ciências", "Experimentos",
                StatusCategoria.REMOVIDA, criada, atualizada);

        assertEquals(new CategoriaResponseDTO(id, "Ciências", "Experimentos",
                StatusCategoria.REMOVIDA, criada, atualizada), mapper.toResponse(categoria));
    }

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
