package br.com.geniusquest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaInvalidaException;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.exception.GlobalExceptionHandler;
import br.com.geniusquest.service.CategoriaService;

@ExtendWith (MockitoExtension.class)
public class CategoriaControllerTest {
    @Mock 
    private CategoriaService service;

    @InjectMocks 
    private CategoriaController controller;

    private MockMvc mockMvc;

    @BeforeEach 
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CategoriaDTO dto(UUID id, String nome, StatusCategoria status) {
        return new CategoriaDTO(id, nome, "Descrição de " + nome, status, null, null);
    }

    @Test 
    void cadastrarDeveRetornar201ComLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.cadastrar(any(CategoriaDTO.class)))
                .thenReturn(dto(id, "Tecnologia", StatusCategoria.PENDENTE));

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tecnologia\",\"descricao\":\"Sobre tecnologia\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/categorias/" + id))
                .andExpect(jsonPath("$.nome").value("Tecnologia"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void cadastrarInvalidoDeveRetornar400() throws Exception {
        when(service.cadastrar(any(CategoriaDTO.class)))
                .thenThrow(new CategoriaInvalidaException("O nome é obrigatório"));

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Sem nome\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrarDuplicadoDeveRetornar409() throws Exception {
        when(service.cadastrar(any(CategoriaDTO.class)))
                .thenThrow(new CategoriaJaCadastradaException());

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tecnologia\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listarDeveRetornar200() throws Exception {
        when(service.listar()).thenReturn(List.of(
                dto(UUID.randomUUID(), "Arte", StatusCategoria.APROVADA),
                dto(UUID.randomUUID(), "História", StatusCategoria.PENDENTE)));

        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Arte"));
    }

    @Test
    void listarAprovadasDeveRetornar200() throws Exception {
        when(service.listarAprovadas()).thenReturn(List.of(
                dto(UUID.randomUUID(), "Arte", StatusCategoria.APROVADA)));

        mockMvc.perform(get("/api/categorias/aprovadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APROVADA"));
    }

    @Test
    void listarPendentesDeveRetornar200() throws Exception {
        when(service.listarPendentes()).thenReturn(List.of(
                dto(UUID.randomUUID(), "Esportes", StatusCategoria.PENDENTE)));

        mockMvc.perform(get("/api/categorias/pendentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    void listarRemovidasDeveRetornar200() throws Exception {
        when(service.listarRemovidas()).thenReturn(List.of(
                dto(UUID.randomUUID(), "Geografia", StatusCategoria.REMOVIDA)));

        mockMvc.perform(get("/api/categorias/removidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REMOVIDA"));
    }

    @Test
    void buscarPorIdDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id))
                .thenReturn(dto(id, "Tecnologia", StatusCategoria.APROVADA));

        mockMvc.perform(get("/api/categorias/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Tecnologia"));
    }

    @Test
    void buscarPorIdInexistenteDeveRetornar404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(id)).thenThrow(new CategoriaNaoEncontradaException());

        mockMvc.perform(get("/api/categorias/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizarDeveRetornar200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizar(eq(id), any(CategoriaDTO.class)))
                .thenReturn(dto(id, "Ciência", StatusCategoria.APROVADA));

        mockMvc.perform(patch("/api/categorias/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ciência\",\"status\":\"APROVADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ciência"))
                .andExpect(jsonPath("$.status").value("APROVADA"));
    }

    @Test
    void atualizarInvalidoDeveRetornar400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizar(eq(id), any(CategoriaDTO.class)))
                .thenThrow(new CategoriaInvalidaException("O nome da categoria deve possuir entre 2 e 80 caracteres"));

        mockMvc.perform(patch("/api/categorias/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarInexistenteDeveRetornar404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizar(eq(id), any(CategoriaDTO.class)))
                .thenThrow(new CategoriaNaoEncontradaException());

        mockMvc.perform(patch("/api/categorias/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ciência\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizarComNomeDuplicadoDeveRetornar409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.atualizar(eq(id), any(CategoriaDTO.class)))
                .thenThrow(new CategoriaJaCadastradaException());

        mockMvc.perform(patch("/api/categorias/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"História\"}"))
                .andExpect(status().isConflict());
    }
}
