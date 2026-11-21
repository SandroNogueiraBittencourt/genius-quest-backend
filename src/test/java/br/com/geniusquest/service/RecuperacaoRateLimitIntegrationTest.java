package br.com.geniusquest.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Testa o UPSERT atomico real em PostgreSQL, sem enviar e-mails. */
@SpringBootTest
@ActiveProfiles("test")
class RecuperacaoRateLimitIntegrationTest {
    @Autowired private RecuperacaoRateLimitService limiter;

    @Test
    void permiteTresPedidosPorEmailEBloqueiaQuartoMesmoComIpsDiferentes() {
        String email = "rate-" + UUID.randomUUID() + "@example.com";
        assertTrue(limiter.permitir(email, "192.0.2.10"));
        assertTrue(limiter.permitir(email.toUpperCase(), "192.0.2.11"));
        assertTrue(limiter.permitir(" " + email + " ", "192.0.2.12"));
        assertFalse(limiter.permitir(email, "192.0.2.13"));
    }

    @Test
    void permiteVintePedidosPorIpEBloqueiaVigesimoPrimeiro() {
        String origem = "198.51.100." + (UUID.randomUUID().getLeastSignificantBits() & 0xFF);
        String prefixo = "origin-" + UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.permitir(prefixo + i + "@example.com", origem));
        }
        assertFalse(limiter.permitir(prefixo + "ultimo@example.com", origem));
    }
    @Test
    void bloqueiaSextaValidacaoDoMesmoCodigoApesarDeIpsDiferentes() {
        String codigo = CodigoRecuperacao.gerar();
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.permitirValidacao(codigo, "192.0.2." + i));
        }
        assertFalse(limiter.permitirValidacao(codigo, "192.0.2.6"));
    }

    @Test
    void bloqueiaVigesimaPrimeiraValidacaoNoMesmoIp() {
        String origem = "teste-validacao-" + UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.permitirValidacao(CodigoRecuperacao.gerar(), origem));
        }
        assertFalse(limiter.permitirValidacao(CodigoRecuperacao.gerar(), origem));
    }

}
