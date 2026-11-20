package br.com.geniusquest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BrevoApiEmailSenderTest {
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> credential = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(201);
    private URI endpoint;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            credential.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void enviaCorpoParaApiSemExporSegredoEmResposta() throws Exception {
        var client = new BrevoApiEmailSender("chave-apenas-para-teste",
                endpoint, HttpClient.newHttpClient());
        client.enviar("alguem@example.com", "Teste", "remetente@example.com",
                "<a href='https://example.com/redefinir-senha?token=teste'>Recuperar</a>");
        assertEquals("chave-apenas-para-teste", credential.get());

        JsonNode json = new ObjectMapper().readTree(body.get());
        assertEquals("remetente@example.com", json.path("sender").path("email").asText());
        assertEquals("alguem@example.com", json.path("to").get(0).path("email").asText());
        assertFalse(json.path("to").get(0).path("contactPixelTrackingConsent").asBoolean(true));
        assertEquals("Genius Quest: recuperação de senha", json.path("subject").asText());
        assertTrue(json.path("htmlContent").asText().contains("token=teste"));
    }

    @Test
    void erroHttpNaoRevelaCredencialNemConteudoDoEmail() {
        status.set(401);
        var client = new BrevoApiEmailSender("chave-apenas-para-teste",
                endpoint, HttpClient.newHttpClient());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.enviar("alguem@example.com", "Teste",
                        "remetente@example.com", "conteudo-sensivel"));
        assertEquals("Brevo API recusou envio: HTTP 401", ex.getMessage());
        assertFalse(ex.getMessage().contains("chave-apenas-para-teste"));
        assertFalse(ex.getMessage().contains("conteudo-sensivel"));
    }

    @Test
    void naoFazRequisicaoSemChave() {
        var client = new BrevoApiEmailSender("", endpoint, HttpClient.newHttpClient());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.enviar("alguem@example.com", "Teste",
                        "remetente@example.com", "HTML"));
        assertEquals("BREVO_API_KEY ausente", ex.getMessage());
        assertNull(body.get());
    }
}
