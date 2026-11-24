package br.com.geniusquest.controller;

import br.com.geniusquest.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import tools.jackson.databind.json.JsonMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private DataSource dataSource;

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

    @Test
    void trocaExigeSenhaEMantemEmailAnteriorAteConfirmar() throws Exception {
        String email = "anterior-" + UUID.randomUUID() + "@example.com";
        String novo = "novo-" + UUID.randomUUID() + "@example.com";
        String senha = "SenhaTeste123!";
        enviar("/api/usuarios", """
                {"nome":"Pessoa Teste","email":"%s","senha":"%s"}
                """.formatted(email, senha)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificado").value(false));
        ArgumentCaptor<String> cadastro = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), cadastro.capture());
        confirmar(email, cadastro.getValue()).andExpect(status().isNoContent());
        String token = logar(email, senha);
        UUID id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);

        alterar(id, token, novo, "senha incorreta").andExpect(status().isForbidden());
        alterar(id, token, novo, senha).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        logar(email, senha);

        enviar("/api/auth/esqueci-senha", """
                {"email":"%s"}
                """.formatted(email)).andExpect(status().isOk());
        ArgumentCaptor<String> recuperacao = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoRecuperacao(eq(email), eq("Pessoa Teste"),
                recuperacao.capture(), anyString());
        assertEquals(16, recuperacao.getValue().length());

        ArgumentCaptor<String> troca = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(novo), eq("Pessoa Teste"), troca.capture());
        assertTrue(troca.getValue().matches("[0-9]{10}"));
        confirmar(novo, troca.getValue()).andExpect(status().isBadRequest());
        String corpo = """
                {"codigo":"%s"}
                """.formatted(troca.getValue());
        enviar("/api/auth/confirmar-troca-email", corpo).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/confirmar-troca-email").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        logar(novo, senha);
        enviar("/api/auth/redefinir-senha", """
                {"token":"%s","novaSenha":"NovaSenha123!"}
                """.formatted(recuperacao.getValue())).andExpect(status().isBadRequest());
    }

    @Test
    void atualizaBancoV21ComContaERecuperacaoExistentes() {
        String schema = "upgrade_email_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).target("21").load().migrate();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".usuarios (id, nome, email, senha) VALUES (?, ?, ?, ?)",
                id, "Usuário V21", "legado@example.com", "hash");
        jdbc.update("INSERT INTO " + schema + ".token_recuperacao_senha"
                + " (id, usuario_id, token_hash, expira_em, criado_em) VALUES (?, ?, ?, now() + INTERVAL '10 minutes', now())",
                UUID.randomUUID(), id, "hash-existente");

        Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT email_verificado FROM " + schema
                + ".usuarios WHERE id = ?", Boolean.class, id));
        assertEquals("hash-existente", jdbc.queryForObject("SELECT token_hash FROM " + schema
                + ".token_recuperacao_senha WHERE usuario_id = ?", String.class, id));
    }

    private String logar(String email, String senha) throws Exception {
        String body = enviar("/api/auth/login", """
                {"email":"%s","senha":"%s"}
                """.formatted(email, senha)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("token").asText();
    }

    private ResultActions alterar(UUID id, String token, String novo, String senha) throws Exception {
        return mvc.perform(patch("/api/usuarios/{id}", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","senhaAtual":"%s"}
                        """.formatted(novo, senha)));
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
