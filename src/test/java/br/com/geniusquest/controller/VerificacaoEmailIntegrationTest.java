package br.com.geniusquest.controller;

import br.com.geniusquest.service.EmailService;
import br.com.geniusquest.security.SessaoCookie;
import jakarta.servlet.http.Cookie;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
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
    private static final String SENHA_CONTA_TESTE = "SenhaTeste123!";
    private final String origem = "2001:db8::"
            + UUID.randomUUID().toString().substring(24).replaceFirst("(.{4})(.{4})(.{4})", "$1:$2:$3");

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
        mvc.perform(post("/api/auth/confirmar-troca-email").with(this::definirOrigem)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isNoContent());
        mvc.perform(get("/api/usuarios/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        logar(novo, senha);
        enviar("/api/auth/redefinir-senha", """
                {"token":"%s","novaSenha":"NovaSenha123!"}
                """.formatted(recuperacao.getValue())).andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaEnderecoOcupadoAntesEDepoisDoPedidoDeTroca() throws Exception {
        String email = "titular-" + UUID.randomUUID() + "@example.com";
        String ocupado = "ocupado-" + UUID.randomUUID() + "@example.com";
        String novo = "disputado-" + UUID.randomUUID() + "@example.com";
        String senha = "SenhaTeste123!";
        enviar("/api/usuarios", """
                {"nome":"Pessoa Teste","email":"%s","senha":"%s"}
                """.formatted(email, senha)).andExpect(status().isCreated());
        ArgumentCaptor<String> cadastro = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), cadastro.capture());
        confirmar(email, cadastro.getValue()).andExpect(status().isNoContent());
        UUID id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
        String token = logar(email, senha);

        enviar("/api/usuarios", """
                {"nome":"Outra Pessoa","email":"%s","senha":"%s"}
                """.formatted(ocupado, senha)).andExpect(status().isCreated());
        alterar(id, token, ocupado.toUpperCase(Locale.ROOT), senha).andExpect(status().isConflict());
        alterar(id, token, "", senha).andExpect(status().isBadRequest());
        alterar(id, token, novo, senha).andExpect(status().isOk());
        ArgumentCaptor<String> troca = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(novo), eq("Pessoa Teste"), troca.capture());

        enviar("/api/usuarios", """
                {"nome":"Outra Pessoa","email":"%s","senha":"%s"}
                """.formatted(novo, senha)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/confirmar-troca-email").with(this::definirOrigem)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"codigo":"%s"}
                        """.formatted(troca.getValue()))).andExpect(status().isConflict());
        logar(email, senha);
    }

    @Test
    void rejeitaCodigoDeTrocaExpiradoEMantemEmailAtual() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        String codigo = solicitarTroca(conta, destino);
        jdbc.update("""
                UPDATE codigos_verificacao_email SET expira_em = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE usuario_id = ?
                """, conta.id());

        confirmarTroca(conta, codigo).andExpect(status().isBadRequest());
        assertEquals(conta.email(), emailDaConta(conta));
        logar(conta.email(), SENHA_CONTA_TESTE);
    }

    @Test
    void aceitaSomenteOCodigoDaSolicitacaoMaisRecente() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String primeiro = "primeiro-" + UUID.randomUUID() + "@example.com";
        String segundo = "segundo-" + UUID.randomUUID() + "@example.com";
        String codigoAnterior = solicitarTroca(conta, primeiro);
        String codigoAtual = solicitarTroca(conta, segundo);

        confirmarTroca(conta, codigoAnterior).andExpect(status().isBadRequest());
        assertEquals(conta.email(), emailDaConta(conta));
        confirmarTroca(conta, codigoAtual).andExpect(status().isNoContent());
        assertEquals(segundo, emailDaConta(conta));
    }

    @Test
    void impedeTrocaDeEmailDeOutraContaMesmoComSenhaCorreta() throws Exception {
        ContaTeste titular = cadastrarContaVerificada();
        ContaTeste outraConta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";

        alterar(titular.id(), outraConta.token(), destino, SENHA_CONTA_TESTE)
                .andExpect(status().isForbidden());
        assertEquals(titular.email(), emailDaConta(titular));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM codigos_verificacao_email WHERE usuario_id = ?", Integer.class, titular.id()));
        verify(emailService, never()).enviarCodigoVerificacao(eq(destino), anyString(), anyString());
    }

    @Test
    void apenasUmaContaConfirmaOMesmoEnderecoEmParalelo() throws Exception {
        ContaTeste primeira = cadastrarContaVerificada();
        ContaTeste segunda = cadastrarContaVerificada();
        String destino = "disputa-" + UUID.randomUUID() + "@example.com";
        alterar(primeira.id(), primeira.token(), destino, SENHA_CONTA_TESTE).andExpect(status().isOk());
        alterar(segunda.id(), segunda.token(), destino, SENHA_CONTA_TESTE).andExpect(status().isOk());
        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).enviarCodigoVerificacao(eq(destino), eq("Pessoa Teste"), codigos.capture());

        var inicio = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        int statusPrimeira;
        int statusSegunda;
        try {
            var respostaPrimeira = executor.submit(() -> {
                inicio.await(5, TimeUnit.SECONDS);
                return confirmarTroca(primeira, codigos.getAllValues().get(0)).andReturn().getResponse().getStatus();
            });
            var respostaSegunda = executor.submit(() -> {
                inicio.await(5, TimeUnit.SECONDS);
                return confirmarTroca(segunda, codigos.getAllValues().get(1)).andReturn().getResponse().getStatus();
            });
            statusPrimeira = respostaPrimeira.get(10, TimeUnit.SECONDS);
            statusSegunda = respostaSegunda.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(204, Math.min(statusPrimeira, statusSegunda));
        assertEquals(409, Math.max(statusPrimeira, statusSegunda));
        ContaTeste vencedora = statusPrimeira == 204 ? primeira : segunda;
        ContaTeste perdedora = statusPrimeira == 409 ? primeira : segunda;
        assertEquals(destino, emailDaConta(vencedora));
        assertEquals(perdedora.email(), emailDaConta(perdedora));
    }

    @Test
    void exigeAutenticacaoParaSolicitarTrocaDeEmail() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        mvc.perform(patch("/api/usuarios/{id}", conta.id())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","senhaAtual":"%s"}
                        """.formatted(destino, SENHA_CONTA_TESTE)))
                .andExpect(status().isUnauthorized());
        assertEquals(conta.email(), emailDaConta(conta));
        verify(emailService, never()).enviarCodigoVerificacao(eq(destino), anyString(), anyString());
    }

    @Test
    void codigoDeTrocaSoPodeSerConfirmadoPelaContaQueSolicitou() throws Exception {
        ContaTeste titular = cadastrarContaVerificada();
        ContaTeste outraConta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        String codigo = solicitarTroca(titular, destino);

        confirmarTroca(outraConta, codigo).andExpect(status().isBadRequest());
        assertEquals(outraConta.email(), emailDaConta(outraConta));
        assertEquals(titular.email(), emailDaConta(titular));
        confirmarTroca(titular, codigo).andExpect(status().isNoContent());
        assertEquals(destino, emailDaConta(titular));
    }

    @Test
    void codigoJaUsadoNaoPodeSerReutilizadoComSessaoNova() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        String codigo = solicitarTroca(conta, destino);
        confirmarTroca(conta, codigo).andExpect(status().isNoContent());

        ContaTeste novaSessao = new ContaTeste(conta.id(), destino, logar(destino, SENHA_CONTA_TESTE));
        confirmarTroca(novaSessao, codigo).andExpect(status().isBadRequest());
        assertEquals(destino, emailDaConta(novaSessao));
    }

    @Test
    void aceitaCodigoComEspacoEHifenENormalizaNovoEndereco() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        alterar(conta.id(), conta.token(), destino.toUpperCase(Locale.ROOT), SENHA_CONTA_TESTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(conta.email()));
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(destino), eq("Pessoa Teste"), codigo.capture());
        String valor = codigo.getValue();
        String formatado = valor.substring(0, 3) + " " + valor.substring(3, 6) + "-" + valor.substring(6);

        confirmarTroca(conta, formatado).andExpect(status().isNoContent());
        assertEquals(destino, emailDaConta(conta));
    }

    @Test
    void senhaIncorretaNaoInvalidaCodigoDeTrocaJaEnviado() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        String outroDestino = "outro-destino-" + UUID.randomUUID() + "@example.com";
        String codigo = solicitarTroca(conta, destino);

        alterar(conta.id(), conta.token(), outroDestino, "SenhaIncorreta123!")
                .andExpect(status().isForbidden());
        verify(emailService, never()).enviarCodigoVerificacao(eq(outroDestino), anyString(), anyString());
        confirmarTroca(conta, codigo).andExpect(status().isNoContent());
        assertEquals(destino, emailDaConta(conta));
    }

    @Test
    void cookieAnteriorNaoRestauraSessaoDepoisDaTrocaDeEmail() throws Exception {
        ContaTeste conta = cadastrarContaVerificada();
        String destino = "destino-" + UUID.randomUUID() + "@example.com";
        String codigo = solicitarTroca(conta, destino);
        restaurarSessao(conta.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(conta.email()));

        confirmarTroca(conta, codigo).andExpect(status().isNoContent());
        restaurarSessao(conta.token()).andExpect(status().isUnauthorized());
        restaurarSessao(logar(destino, SENHA_CONTA_TESTE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(destino));
    }

    private ResultActions restaurarSessao(String token) throws Exception {
        return mvc.perform(post(SessaoCookie.ROTA_RESTAURACAO)
                .cookie(new Cookie(SessaoCookie.NOME, token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    void trocaSenhaSomenteDoTitularEInvalidaSessaoERecuperacao() throws Exception {
        String email = "troca-senha-" + UUID.randomUUID() + "@example.com";
        String senhaAtual = "SenhaTeste123!";
        String novaSenha = "NovaSenha123!";
        enviar("/api/usuarios", """
                {"nome":"Pessoa Teste","email":"%s","senha":"%s"}
                """.formatted(email, senhaAtual)).andExpect(status().isCreated());
        ArgumentCaptor<String> cadastro = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), cadastro.capture());
        confirmar(email, cadastro.getValue()).andExpect(status().isNoContent());
        UUID id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
        String token = logar(email, senhaAtual);
        String outroDispositivo = logar(email, senhaAtual);
        assertNotEquals(token, outroDispositivo);
        String corpo = """
                {"senhaAtual":"%s","novaSenha":"%s"}
                """.formatted(senhaAtual, novaSenha);

        mvc.perform(patch("/api/usuarios/{id}/senha", id)
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        trocarSenha(UUID.randomUUID(), token, corpo).andExpect(status().isForbidden());
        trocarSenha(id, token, corpo.replace(senhaAtual, "SenhaIncorreta1!"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("A senha atual está incorreta."));
        trocarSenha(id, token, corpo.replace(novaSenha, "senhafraca123"))
                .andExpect(status().isBadRequest());
        trocarSenha(id, token, """
                {"novaSenha":"%s"}
                """.formatted(novaSenha)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senhaAtual").exists());
        mvc.perform(get("/api/usuarios/{id}", id).header("Authorization", "Bearer " + outroDispositivo))
                .andExpect(status().isOk());

        enviar("/api/auth/esqueci-senha", """
                {"email":"%s"}
                """.formatted(email)).andExpect(status().isOk());
        ArgumentCaptor<String> recuperacao = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoRecuperacao(eq(email), eq("Pessoa Teste"),
                recuperacao.capture(), anyString());

        trocarSenha(id, token, corpo).andExpect(status().isNoContent());
        for (String sessaoAnterior : java.util.List.of(token, outroDispositivo)) {
            mvc.perform(get("/api/usuarios/{id}", id).header("Authorization", "Bearer " + sessaoAnterior))
                    .andExpect(status().isUnauthorized());
        }
        enviar("/api/auth/login", """
                {"email":"%s","senha":"%s"}
                """.formatted(email, senhaAtual)).andExpect(status().isUnauthorized());
        enviar("/api/auth/redefinir-senha", """
                {"token":"%s","novaSenha":"OutraSenha123!"}
                """.formatted(recuperacao.getValue())).andExpect(status().isBadRequest());
        logar(email, novaSenha);
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

    private record ContaTeste(UUID id, String email, String token) {}

    private ContaTeste cadastrarContaVerificada() throws Exception {
        String email = "conta-" + UUID.randomUUID() + "@example.com";
        enviar("/api/usuarios", """
                {"nome":"Pessoa Teste","email":"%s","senha":"%s"}
                """.formatted(email, SENHA_CONTA_TESTE)).andExpect(status().isCreated());
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(email), eq("Pessoa Teste"), codigo.capture());
        confirmar(email, codigo.getValue()).andExpect(status().isNoContent());
        UUID id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", UUID.class, email);
        return new ContaTeste(id, email, logar(email, SENHA_CONTA_TESTE));
    }

    private String solicitarTroca(ContaTeste conta, String destino) throws Exception {
        alterar(conta.id(), conta.token(), destino, SENHA_CONTA_TESTE).andExpect(status().isOk());
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigoVerificacao(eq(destino), eq("Pessoa Teste"), codigo.capture());
        return codigo.getValue();
    }

    private ResultActions confirmarTroca(ContaTeste conta, String codigo) throws Exception {
        return mvc.perform(post("/api/auth/confirmar-troca-email").with(this::definirOrigem)
                .header("Authorization", "Bearer " + conta.token())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"codigo":"%s"}
                        """.formatted(codigo)));
    }

    private String emailDaConta(ContaTeste conta) {
        return jdbc.queryForObject("SELECT email FROM usuarios WHERE id = ?", String.class, conta.id());
    }

    private String logar(String email, String senha) throws Exception {
        String body = enviar("/api/auth/login", """
                {"email":"%s","senha":"%s"}
                """.formatted(email, senha)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("token").asString();
    }

    private ResultActions alterar(UUID id, String token, String novo, String senha) throws Exception {
        return mvc.perform(patch("/api/usuarios/{id}", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","senhaAtual":"%s"}
                        """.formatted(novo, senha)));
    }

    private ResultActions trocarSenha(UUID id, String token, String corpo) throws Exception {
        return mvc.perform(patch("/api/usuarios/{id}/senha", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions confirmar(String email, String codigo) throws Exception {
        return enviar("/api/auth/verificar-email", """
                {"email":"%s","codigo":"%s"}
                """.formatted(email, codigo));
    }

    private ResultActions enviar(String caminho, String json) throws Exception {
        return mvc.perform(post(caminho).with(this::definirOrigem)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpServletRequest definirOrigem(MockHttpServletRequest request) {
        request.setRemoteAddr(origem);
        return request;
    }
}
