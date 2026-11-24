package br.com.geniusquest.controller;

import br.com.geniusquest.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacaoEmailIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void exigeConfirmacaoEAceitaSomenteOCodigoMaisRecenteUmaVez() throws Exception {
        String email = "confirmacao-" + UUID.randomUUID() + "@example.com";
        enviar("/api/usuarios", """
                {"nome":"Pessoa Teste","email":"%s","senha":"SenhaTeste123!"}
                """.formatted(email)).andExpect(status().isCreated());

        String credenciais = """
                {"email":"%s","senha":"SenhaTeste123!"}
                """.formatted(email);
        enviar("/api/auth/login", credenciais).andExpect(status().isForbidden());
        ArgumentCaptor<String> primeiroCodigo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), primeiroCodigo.capture());
        jdbc.update("""
                UPDATE codigos_verificacao_email SET expira_em = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)
                """, email);
        String mensagem = "Código inválido, expirado ou já utilizado. Confira o código ou solicite outro.";
        confirmar(email, primeiroCodigo.getValue()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(mensagem));

        enviar("/api/auth/reenviar-verificacao", """
                {"email":"%s"}
                """.formatted(email)).andExpect(status().isAccepted());

        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), codigos.capture());
        String primeiro = codigos.getAllValues().get(0);
        String atual = codigos.getValue();

        confirmar(email, primeiro).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(mensagem));
        confirmar(email, atual).andExpect(status().isNoContent());
        confirmar(email, atual).andExpect(status().isBadRequest());
        enviar("/api/auth/login", credenciais).andExpect(status().isOk());
    }

    private ResultActions confirmar(String email, String codigo) throws Exception {
        return enviar("/api/auth/verificar-email", """
                {"email":"%s","codigo":"%s"}
                """.formatted(email, codigo));
    }

    private ResultActions enviar(String caminho, String json) throws Exception {
        return mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
