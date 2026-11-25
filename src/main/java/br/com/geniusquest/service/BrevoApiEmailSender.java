package br.com.geniusquest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrevoApiEmailSender {
    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public BrevoApiEmailSender(@Value("${app.mail.brevo.api-key:}") String apiKey) {
        this(apiKey, ENDPOINT, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    // Construtor para testes com servidor HTTP local. O endpoint de producao e fixo.
    BrevoApiEmailSender(String apiKey, URI endpoint, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    public void enviar(String destinatario, String nome, String remetente, String html) {
        enviar(destinatario, nome, remetente, "Genius Quest: recuperação de senha", html);
    }

    public void enviar(String destinatario, String nome, String remetente, String assunto, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY ausente");
        }
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalStateException("MAIL_FROM ausente");
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("sender", Map.of("name", "Genius Quest", "email", remetente));
        corpo.put("to", List.of(Map.of(
                "email", destinatario,
                "name", nome,
                // Efeito depende da configuracao de consentimento no painel Brevo.
                "contactPixelTrackingConsent", false)));
        corpo.put("subject", assunto);
        corpo.put("htmlContent", html);

        try {
            String json = mapper.writeValueAsString(corpo);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(12))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            // A Brevo confirma a criacao do envio com HTTP 201.
            if (response.statusCode() != 201) {
                throw new IllegalStateException(
                        "Brevo API recusou envio: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio HTTPS da Brevo interrompido");
        } catch (IOException e) {
            // Nao registrar a requisicao, a chave, o email ou o link com token.
            throw new IllegalStateException("Falha de comunicacao com a Brevo");
        }
    }
}
