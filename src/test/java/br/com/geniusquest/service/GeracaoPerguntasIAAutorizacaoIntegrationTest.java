package br.com.geniusquest.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class GeracaoPerguntasIAAutorizacaoIntegrationTest {
    @Autowired
    private GeracaoPerguntasIAService geracao;

    @Test
    @WithMockUser(roles = "USUARIO")
    void usuarioComumNaoPodeInvocarServicoMesmoSemController() {
        assertThrows(AccessDeniedException.class,
                () -> geracao.gerarCandidatas(UUID.randomUUID(), 1));
    }
}
