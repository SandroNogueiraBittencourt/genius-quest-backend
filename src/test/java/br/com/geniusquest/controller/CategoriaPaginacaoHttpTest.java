package br.com.geniusquest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CategoriaPaginacaoHttpTest {
    @Mock
    CategoriaService service;

    @InjectMocks
    CategoriaController controller;

    MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private CategoriaResponseDTO categoria() {
        return new CategoriaResponseDTO(
                UUID.randomUUID(), "Ciência", "Conhecimento científico",
                StatusCategoria.APROVADA, Instant.now(), Instant.now());
    }

    @Test
    void semParametroPageMantemFormatoDeLista() throws Exception {
        when(service.listarAprovadas()).thenReturn(List.of(categoria()));
        mvc.perform(get("/api/categorias/aprovadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ciência"));
    }

    @Test
    void comParametroPageRetornaConteudoEMetadados() throws Exception {
        var pagina = new PageImpl<>(List.of(categoria()), PageRequest.of(0, 10), 1);
        when(service.listarAprovadas(any(Pageable.class))).thenReturn(pagina);
        mvc.perform(get("/api/categorias/aprovadas?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Ciência"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.size").value(10));
    }

    @Test
    void paginaInvalidaRetorna400() throws Exception {
        mvc.perform(get("/api/categorias/aprovadas?page=-1&size=10"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/categorias/aprovadas?page=0&size=101"))
                .andExpect(status().isBadRequest());
    }
}
