package br.com.geniusquest.repository;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenRevogadoRepositoryTest {
    @Autowired TokenRevogadoRepository tokens;

    @Test
    void removeTokenNoInstanteDaExpiracaoEPreservaTokenVigente() {
        Instant agora = Instant.parse("2026-10-01T12:00:00Z");
        String expirado = UUID.randomUUID().toString();
        String vigente = UUID.randomUUID().toString();
        tokens.registrar(expirado, agora);
        tokens.registrar(vigente, agora.plusSeconds(1));

        tokens.removerExpirados(agora);

        assertFalse(tokens.existe(expirado));
        assertTrue(tokens.existe(vigente));
    }

    @Test
    void revogarOMesmoTokenDuasVezesPreservaAExpiracaoOriginal() {
        Instant expiracao = Instant.parse("2026-10-01T12:00:00Z");
        String hash = UUID.randomUUID().toString();
        tokens.registrar(hash, expiracao);
        tokens.registrar(hash, expiracao.plusSeconds(300));

        assertTrue(tokens.existe(hash));
        tokens.removerExpirados(expiracao);
        assertFalse(tokens.existe(hash));
    }
}
