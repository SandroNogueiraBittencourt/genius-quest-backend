package br.com.geniusquest.controller;

import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.exception.EmailJaCadastradoException;
import br.com.geniusquest.exception.GlobalExceptionHandler;
import br.com.geniusquest.service.UsuarioService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsuarioControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private UsuarioService service;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        UsuarioController controller =
                new UsuarioController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void deveCadastrarUsuarioERetornar201()
            throws Exception {

        UUID id = UUID.randomUUID();

        UsuarioResponseDTO resposta =
                new UsuarioResponseDTO(
                        id,
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        PerfilUsuario.USUARIO,
                        0,
                        true,
                        Instant.now(),
                        Instant.now()
                );

        when(service.cadastrar(any(UsuarioCadastroDTO.class)))
                .thenReturn(resposta);

        UsuarioCadastroDTO request =
                new UsuarioCadastroDTO(
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        "SenhaSegura123!"
                );

        mockMvc.perform(
                        post("/api/usuarios")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/usuarios/" + id
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(id.toString())
                )
                .andExpect(
                        jsonPath("$.nome")
                                .value("Sandro Nogueira")
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(
                                        "sandro@geniusquest.com"
                                )
                )
                .andExpect(
                        jsonPath("$.perfil")
                                .value("USUARIO")
                )
                .andExpect(
                        jsonPath("$.moedas")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.ativo")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.senha")
                                .doesNotExist()
                );
    }

    @Test
    void deveRetornar409QuandoEmailJaExistir()
            throws Exception {

        when(service.cadastrar(any(UsuarioCadastroDTO.class)))
                .thenThrow(
                        new EmailJaCadastradoException()
                );

        UsuarioCadastroDTO request =
                new UsuarioCadastroDTO(
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        "SenhaSegura123!"
                );

        mockMvc.perform(
                        post("/api/usuarios")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.mensagem")
                                .value("E-mail já cadastrado")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/usuarios")
                );
    }

    @Test
    void deveRetornar400QuandoDadosForemInvalidos()
            throws Exception {

        String json = """
                {
                  "nome": "Sa",
                  "email": "email-invalido",
                  "senha": "123"
                }
                """;

        mockMvc.perform(
                        post("/api/usuarios")
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
                        jsonPath("$.mensagem")
                                .value(
                                        "Existem campos inválidos na requisição"
                                )
                )
                .andExpect(
                        jsonPath("$.campos.nome")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.campos.email")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.campos.senha")
                                .exists()
                );
    }
}
