package br.com.geniusquest.controller;

import br.com.geniusquest.dto.usuario.UsuarioAtualizacaoDTO;
import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.exception.UsuarioNaoEncontradoException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

class UsuarioControllerTest {

        private MockMvc mockMvc;

        private ObjectMapper objectMapper;

        @Mock
        private UsuarioService service;

        @BeforeEach
        void setUp() {

                MockitoAnnotations.openMocks(this);

                UsuarioController controller = new UsuarioController(service);

                mockMvc = MockMvcBuilders
                                .standaloneSetup(controller)
                                .setControllerAdvice(
                                                new GlobalExceptionHandler())
                                .build();

                objectMapper = new ObjectMapper();
                objectMapper.findAndRegisterModules();
        }

        @Test
        void deveCadastrarUsuarioERetornar201()
                        throws Exception {

                UUID id = UUID.randomUUID();

                UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                                id,
                                "Sandro Nogueira",
                                "sandro@geniusquest.com",
                                PerfilUsuario.USUARIO,
                                0,
                                true,
                                Instant.now(),
                                Instant.now());

                when(service.cadastrar(any(UsuarioCadastroDTO.class)))
                                .thenReturn(resposta);

                UsuarioCadastroDTO request = new UsuarioCadastroDTO(
                                "Sandro Nogueira",
                                "sandro@geniusquest.com",
                                "SenhaSegura123!");

                mockMvc.perform(
                                post("/api/usuarios")
                                                .contentType(
                                                                MediaType.APPLICATION_JSON)
                                                .content(
                                                                objectMapper.writeValueAsString(
                                                                                request)))
                                .andExpect(status().isCreated())
                                .andExpect(
                                                header().string(
                                                                "Location",
                                                                "/api/usuarios/" + id))
                                .andExpect(
                                                jsonPath("$.id")
                                                                .value(id.toString()))
                                .andExpect(
                                                jsonPath("$.nome")
                                                                .value("Sandro Nogueira"))
                                .andExpect(
                                                jsonPath("$.email")
                                                                .value(
                                                                                "sandro@geniusquest.com"))
                                .andExpect(
                                                jsonPath("$.perfil")
                                                                .value("USUARIO"))
                                .andExpect(
                                                jsonPath("$.moedas")
                                                                .value(0))
                                .andExpect(
                                                jsonPath("$.ativo")
                                                                .value(true))
                                .andExpect(
                                                jsonPath("$.senha")
                                                                .doesNotExist());
        }

        @Test
        void deveBuscarUsuarioPorIdERetornar200()
                        throws Exception {

                UUID id = UUID.randomUUID();

                UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                                id,
                                "Sandro Nogueira",
                                "sandro@geniusquest.com",
                                PerfilUsuario.USUARIO,
                                0,
                                true,
                                Instant.now(),
                                Instant.now());

                when(service.buscarPorId(id))
                                .thenReturn(resposta);

                mockMvc.perform(
                                get("/api/usuarios/{id}", id)
                                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(
                                                jsonPath("$.id")
                                                                .value(id.toString()))
                                .andExpect(
                                                jsonPath("$.nome")
                                                                .value("Sandro Nogueira"))
                                .andExpect(
                                                jsonPath("$.email")
                                                                .value("sandro@geniusquest.com"))
                                .andExpect(
                                                jsonPath("$.senha")
                                                                .doesNotExist());
        }

        @Test
        void deveRetornar400QuandoAtualizacaoForInvalida()
                        throws Exception {

                String json = """
                                {
                                  "nome": "Sa",
                                  "email": "email-invalido"
                                }
                                """;

                mockMvc.perform(
                                patch(
                                                "/api/usuarios/{id}",
                                                UUID.randomUUID())
                                                .contentType(
                                                                MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isBadRequest())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(400))
                                .andExpect(
                                                jsonPath("$.campos.nome")
                                                                .exists())
                                .andExpect(
                                                jsonPath("$.campos.email")
                                                                .exists());
        }

        @Test
        void deveRetornar409QuandoEmailJaExistir()
                        throws Exception {

                when(service.cadastrar(any(UsuarioCadastroDTO.class)))
                                .thenThrow(
                                                new EmailJaCadastradoException());

                UsuarioCadastroDTO request = new UsuarioCadastroDTO(
                                "Sandro Nogueira",
                                "sandro@geniusquest.com",
                                "SenhaSegura123!");

                mockMvc.perform(
                                post("/api/usuarios")
                                                .contentType(
                                                                MediaType.APPLICATION_JSON)
                                                .content(
                                                                objectMapper.writeValueAsString(
                                                                                request)))
                                .andExpect(status().isConflict())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(409))
                                .andExpect(
                                                jsonPath("$.mensagem")
                                                                .value("E-mail já cadastrado"))
                                .andExpect(
                                                jsonPath("$.path")
                                                                .value("/api/usuarios"));
        }

        @Test
        void deveRetornar409AoAtualizarParaEmailJaExistente()
                        throws Exception {

                UUID id = UUID.randomUUID();

                UsuarioAtualizacaoDTO request = new UsuarioAtualizacaoDTO(
                                "Sandro Nogueira",
                                "outro@geniusquest.com");

                when(service.atualizar(
                                any(UUID.class),
                                any(UsuarioAtualizacaoDTO.class))).thenThrow(
                                                new EmailJaCadastradoException());

                mockMvc.perform(
                                patch("/api/usuarios/{id}", id)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                                objectMapper.writeValueAsString(
                                                                                request)))
                                .andExpect(status().isConflict())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(409))
                                .andExpect(
                                                jsonPath("$.mensagem")
                                                                .value("E-mail já cadastrado"))
                                .andExpect(
                                                jsonPath("$.path")
                                                                .value("/api/usuarios/" + id));
        }

        @Test
        void deveRetornar404QuandoUsuarioNaoExistir()
                        throws Exception {

                UUID id = UUID.randomUUID();

                when(service.buscarPorId(id))
                                .thenThrow(
                                                new UsuarioNaoEncontradoException());

                mockMvc.perform(
                                get("/api/usuarios/{id}", id)
                                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(404))
                                .andExpect(
                                                jsonPath("$.mensagem")
                                                                .value("Usuário não encontrado"));
        }

        @Test
        void deveRetornar404AoAtualizarUsuarioInexistente()
                        throws Exception {

                UUID id = UUID.randomUUID();

                UsuarioAtualizacaoDTO request = new UsuarioAtualizacaoDTO(
                                "Sandro Nogueira",
                                "sandro@geniusquest.com");

                when(service.atualizar(
                                any(UUID.class),
                                any(UsuarioAtualizacaoDTO.class))).thenThrow(
                                                new UsuarioNaoEncontradoException());

                mockMvc.perform(
                                patch("/api/usuarios/{id}", id)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                                objectMapper.writeValueAsString(
                                                                                request)))
                                .andExpect(status().isNotFound())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(404))
                                .andExpect(
                                                jsonPath("$.mensagem")
                                                                .value("Usuário não encontrado"))
                                .andExpect(
                                                jsonPath("$.path")
                                                                .value("/api/usuarios/" + id));
        }

        @Test
        void deveAtualizarUsuarioERetornar200()
                        throws Exception {

                UUID id = UUID.randomUUID();

                UsuarioAtualizacaoDTO request = new UsuarioAtualizacaoDTO(
                                "Sandro Nogueira Bittencourt",
                                "sandro.nogueira@geniusquest.com");

                UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                                id,
                                "Sandro Nogueira Bittencourt",
                                "sandro.nogueira@geniusquest.com",
                                PerfilUsuario.USUARIO,
                                0,
                                true,
                                Instant.now(),
                                Instant.now());

                when(service.atualizar(
                                any(UUID.class),
                                any(UsuarioAtualizacaoDTO.class))).thenReturn(resposta);

                mockMvc.perform(
                                patch("/api/usuarios/{id}", id)
                                                .contentType(
                                                                MediaType.APPLICATION_JSON)
                                                .content(
                                                                objectMapper.writeValueAsString(
                                                                                request)))
                                .andExpect(status().isOk())
                                .andExpect(
                                                jsonPath("$.id")
                                                                .value(id.toString()))
                                .andExpect(
                                                jsonPath("$.nome")
                                                                .value(
                                                                                "Sandro Nogueira Bittencourt"))
                                .andExpect(
                                                jsonPath("$.email")
                                                                .value(
                                                                                "sandro.nogueira@geniusquest.com"));
        }

        @Test
        void deveListarUsuariosERetornar200()
                        throws Exception {

                UsuarioResponseDTO usuario1 = new UsuarioResponseDTO(
                                UUID.randomUUID(),
                                "Sandro Nogueira",
                                "sandro@geniusquest.com",
                                PerfilUsuario.USUARIO,
                                0,
                                true,
                                Instant.now(),
                                Instant.now());

                UsuarioResponseDTO usuario2 = new UsuarioResponseDTO(
                                UUID.randomUUID(),
                                "Maria Silva",
                                "maria@geniusquest.com",
                                PerfilUsuario.USUARIO,
                                0,
                                true,
                                Instant.now(),
                                Instant.now());

                when(service.listar())
                                .thenReturn(
                                                List.of(usuario1, usuario2));

                mockMvc.perform(
                                get("/api/usuarios")
                                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(
                                                jsonPath("$.length()")
                                                                .value(2))
                                .andExpect(
                                                jsonPath("$[0].nome")
                                                                .value("Sandro Nogueira"))
                                .andExpect(
                                                jsonPath("$[1].nome")
                                                                .value("Maria Silva"));
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
                                                                MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isBadRequest())
                                .andExpect(
                                                jsonPath("$.status")
                                                                .value(400))
                                .andExpect(
                                                jsonPath("$.mensagem")
                                                                .value(
                                                                                "Existem campos inválidos na requisição"))
                                .andExpect(
                                                jsonPath("$.campos.nome")
                                                                .exists())
                                .andExpect(
                                                jsonPath("$.campos.email")
                                                                .exists())
                                .andExpect(
                                                jsonPath("$.campos.senha")
                                                                .exists());
        }
}