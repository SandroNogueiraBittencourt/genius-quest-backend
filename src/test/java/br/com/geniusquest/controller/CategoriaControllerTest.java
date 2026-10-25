package br.com.geniusquest.controller;

import br.com.geniusquest.dto.categoria.CategoriaAtualizacaoDTO;
import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.exception.GlobalExceptionHandler;
import br.com.geniusquest.service.CategoriaService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private CategoriaService service;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        CategoriaController controller =
                new CategoriaController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private CategoriaResponseDTO criarResposta(
            UUID id,
            String nome,
            StatusCategoria status) {

        return new CategoriaResponseDTO(
                id,
                nome,
                "Descrição de " + nome,
                status,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void deveCadastrarCategoriaERetornar201()
            throws Exception {

        UUID id = UUID.randomUUID();

        CategoriaResponseDTO resposta =
                criarResposta(
                        id,
                        "Tecnologia",
                        StatusCategoria.PENDENTE
                );

        when(service.cadastrar(
                any(CategoriaCadastroDTO.class)
        )).thenReturn(resposta);

        CategoriaCadastroDTO request =
                new CategoriaCadastroDTO(
                        "Tecnologia",
                        "Categoria de tecnologia"
                );

        mockMvc.perform(
                        post("/api/categorias")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/categorias/" + id
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.nome")
                                .value("Tecnologia")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDENTE")
                );
    }

    @Test
    void deveRetornar400QuandoCadastroForInvalido()
            throws Exception {

        String json = """
                {
                  "nome": "   ",
                  "descricao": "Descrição"
                }
                """;

        mockMvc.perform(
                        post("/api/categorias")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.campos.nome")
                                .exists()
                );
    }

    @Test
    void deveRetornar409QuandoCategoriaJaExistir()
            throws Exception {

        when(service.cadastrar(
                any(CategoriaCadastroDTO.class)
        )).thenThrow(
                new CategoriaJaCadastradaException()
        );

        CategoriaCadastroDTO request =
                new CategoriaCadastroDTO(
                        "Tecnologia",
                        "Categoria de tecnologia"
                );

        mockMvc.perform(
                        post("/api/categorias")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.mensagem")
                                .value("Categoria já cadastrada")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/categorias")
                );
    }

    @Test
    void deveListarCategoriasERetornar200()
            throws Exception {

        CategoriaResponseDTO categoria1 =
                criarResposta(
                        UUID.randomUUID(),
                        "Ciência",
                        StatusCategoria.APROVADA
                );

        CategoriaResponseDTO categoria2 =
                criarResposta(
                        UUID.randomUUID(),
                        "Tecnologia",
                        StatusCategoria.PENDENTE
                );

        when(service.listar())
                .thenReturn(
                        List.of(
                                categoria1,
                                categoria2
                        )
                );

        mockMvc.perform(
                        get("/api/categorias")
                                .accept(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].nome")
                                .value("Ciência")
                )
                .andExpect(
                        jsonPath("$[1].nome")
                                .value("Tecnologia")
                );
    }

    @Test
    void deveListarCategoriasAprovadasERetornar200()
            throws Exception {

        CategoriaResponseDTO categoria =
                criarResposta(
                        UUID.randomUUID(),
                        "Ciência",
                        StatusCategoria.APROVADA
                );

        when(service.listarAprovadas())
                .thenReturn(List.of(categoria));

        mockMvc.perform(
                        get("/api/categorias/aprovadas")
                                .accept(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("APROVADA")
                );
    }

    @Test
    void deveBuscarCategoriaPorIdERetornar200()
            throws Exception {

        UUID id = UUID.randomUUID();

        CategoriaResponseDTO resposta =
                criarResposta(
                        id,
                        "Tecnologia",
                        StatusCategoria.PENDENTE
                );

        when(service.buscarPorId(id))
                .thenReturn(resposta);

        mockMvc.perform(
                        get(
                                "/api/categorias/{id}",
                                id
                        )
                                .accept(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.nome")
                                .value("Tecnologia")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDENTE")
                );
    }

    @Test
    void deveRetornar404QuandoCategoriaNaoExistir()
            throws Exception {

        UUID id = UUID.randomUUID();

        when(service.buscarPorId(id))
                .thenThrow(
                        new CategoriaNaoEncontradaException()
                );

        mockMvc.perform(
                        get(
                                "/api/categorias/{id}",
                                id
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.mensagem")
                                .value("Categoria não encontrada")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/categorias/" + id
                                )
                );
    }

    @Test
    void deveAtualizarCategoriaERetornar200()
            throws Exception {

        UUID id = UUID.randomUUID();

        CategoriaAtualizacaoDTO request =
                new CategoriaAtualizacaoDTO(
                        "Ciência",
                        "Categoria de ciência"
                );

        CategoriaResponseDTO resposta =
                new CategoriaResponseDTO(
                        id,
                        "Ciência",
                        "Categoria de ciência",
                        StatusCategoria.PENDENTE,
                        Instant.now(),
                        Instant.now()
                );

        when(service.atualizar(
                any(UUID.class),
                any(CategoriaAtualizacaoDTO.class)
        )).thenReturn(resposta);

        mockMvc.perform(
                        patch(
                                "/api/categorias/{id}",
                                id
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.nome")
                                .value("Ciência")
                )
                .andExpect(
                        jsonPath("$.descricao")
                                .value("Categoria de ciência")
                );
    }

    @Test
    void deveRetornar400QuandoAtualizacaoForInvalida()
            throws Exception {

        String json = """
                {
                  "nome": "   "
                }
                """;

        mockMvc.perform(
                        patch(
                                "/api/categorias/{id}",
                                UUID.randomUUID()
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(json)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.campos.nome")
                                .exists()
                );
    }

    @Test
    void deveAprovarCategoriaERetornar200()
            throws Exception {

        UUID id = UUID.randomUUID();

        CategoriaResponseDTO resposta =
                criarResposta(
                        id,
                        "Tecnologia",
                        StatusCategoria.APROVADA
                );

        when(service.aprovar(id))
                .thenReturn(resposta);

        mockMvc.perform(
                        patch(
                                "/api/categorias/{id}/aprovar",
                                id
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("APROVADA")
                );
    }

    @Test
    void deveRemoverCategoriaERetornar200()
            throws Exception {

        UUID id = UUID.randomUUID();

        CategoriaResponseDTO resposta =
                criarResposta(
                        id,
                        "Tecnologia",
                        StatusCategoria.REMOVIDA
                );

        when(service.remover(id))
                .thenReturn(resposta);

        mockMvc.perform(
                        patch(
                                "/api/categorias/{id}/remover",
                                id
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("REMOVIDA")
                );
    }
}
