package br.com.geniusquest.controller;

import br.com.geniusquest.exception.GlobalExceptionHandler;
import br.com.geniusquest.exception.TokenRecuperacaoInvalidoException;
import br.com.geniusquest.service.RecuperacaoSenhaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecuperarSenhaControllerTest {
    @Mock private RecuperacaoSenhaService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecuperarSenhaController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void deveRetornar200AoSolicitarRecuperacao() throws Exception {
        mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"teste@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(
                        "Se o e-mail estiver cadastrado, enviaremos um link de recuperação."));
        verify(service).solicitar("teste@example.com");
    }

    @Test
    void deveRetornar204AoRedefinirSenha() throws Exception {
        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-valido","novaSenha":"NovaSenha123!"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).redefinir("token-valido", "NovaSenha123!");
    }

    @Test
    void deveRetornar400ParaTokenInvalido() throws Exception {
        var erro = new TokenRecuperacaoInvalidoException();
        doThrow(erro).when(service).redefinir("invalido", "NovaSenha123!");
        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"invalido","novaSenha":"NovaSenha123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(erro.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/auth/redefinir-senha"));
    }
}
