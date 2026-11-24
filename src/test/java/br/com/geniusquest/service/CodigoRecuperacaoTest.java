package br.com.geniusquest.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CodigoRecuperacaoTest {
    @Test
    void geraCodigoDeOitentaBitsSemCaracteresAmbiguos() {
        String anterior = null;
        for (int i = 0; i < 1000; i++) {
            String codigo = CodigoRecuperacao.gerar();
            assertTrue(codigo.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{16}"));
            assertTrue(CodigoRecuperacao.formatoValido(CodigoRecuperacao.paraExibicao(codigo)));
            if (anterior != null) assertNotEquals(anterior, codigo);
            anterior = codigo;
        }
    }

    @Test
    void rejeitaCodigoMalformadoSemExplicitarSegredo() {
        assertFalse(CodigoRecuperacao.formatoValido(null));
        assertFalse(CodigoRecuperacao.formatoValido("AAAA"));
        assertFalse(CodigoRecuperacao.formatoValido("IIII-0000-1111-OOOO"));
        assertEquals("ABCD2345EFGH6789",
                CodigoRecuperacao.normalizar("abcd-2345-efgh-6789"));
    }
}
