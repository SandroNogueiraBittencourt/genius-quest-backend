package br.com.geniusquest.service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limite fixo de 15 minutos: 3 solicitacoes por e-mail e 20 por IP.
 * O contador e transacional, compartilhado e atomico no PostgreSQL.
 * Nunca registra e-mails, IPs ou tokens em texto puro.
 *
 * IMPORTANTE: getRemoteAddr() nao confia em X-Forwarded-For do cliente.
 * Em producao, configurar resolucao do IP real SOMENTE via proxy confiavel.
 */
@Service
public class RecuperacaoRateLimitService {
    private static final long JANELA_SEGUNDOS = 15 * 60;
    private static final int LIMITE_EMAIL = 3;
    private static final int LIMITE_IP = 20;

    private final JdbcTemplate jdbc;
    private final byte[] chaveHmac;

    public RecuperacaoRateLimitService(JdbcTemplate jdbc,
            @Value("${app.jwt.secret}") String segredoBase64) {
        this.jdbc = jdbc;
        this.chaveHmac = java.util.Base64.getDecoder().decode(segredoBase64);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitir(String email, String origem) {
        String emailNormalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String ip = origem == null || origem.isBlank() ? "indefinido" : origem.trim();
        Instant agora = Instant.now();
        Instant inicio = Instant.ofEpochSecond(
                (agora.getEpochSecond() / JANELA_SEGUNDOS) * JANELA_SEGUNDOS);

        // Os dois contadores sempre sao atualizados para reduzir abusos.
        int tentativasIp = incrementar(hmac("ip:" + ip), inicio);
        int tentativasEmail = incrementar(hmac("email:" + emailNormalizado), inicio);
        return tentativasIp <= LIMITE_IP && tentativasEmail <= LIMITE_EMAIL;
    }

    /** Verifica no maximo 20 codigos por IP e cinco por mesmo codigo em 15 minutos.
     *  Contadores sao compartilhados entre instancias e persistem mesmo apos erro de validacao.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean permitirValidacao(String codigo, String origem) {
        String ip = origem == null || origem.isBlank() ? "indefinido" : origem.trim();
        String normalizado = CodigoRecuperacao.normalizar(codigo);
        Instant agora = Instant.now();
        Instant inicio = Instant.ofEpochSecond(
                (agora.getEpochSecond() / JANELA_SEGUNDOS) * JANELA_SEGUNDOS);
        int porIp = incrementar(hmac("validacao:ip:" + ip), inicio);
        int porCodigo = incrementar(hmac("validacao:codigo:" + normalizado), inicio);
        return porIp <= 20 && porCodigo <= 5;
    }

    private int incrementar(String hash, Instant janela) {
        Integer contador = jdbc.queryForObject("""
                INSERT INTO limites_recuperacao_senha (chave_hash, janela_inicio, tentativas)
                VALUES (?, ?, 1)
                ON CONFLICT (chave_hash, janela_inicio)
                DO UPDATE SET tentativas = limites_recuperacao_senha.tentativas + 1
                RETURNING tentativas
                """, Integer.class, hash, Timestamp.from(janela));
        if (contador == null) throw new IllegalStateException("Contador SMTP nulo");
        return contador;
    }

    private String hmac(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chaveHmac, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException erro) {
            throw new IllegalStateException("HMAC indisponivel", erro);
        }
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    public void excluirContadoresAntigos() {
        jdbc.update("DELETE FROM limites_recuperacao_senha "
                + "WHERE janela_inicio < now() - INTERVAL '2 days'");
    }
}
