package br.com.geniusquest.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CodigoRecuperacaoTest {
    @Test
    void geraCodigoDeDezDigitos() {
        for (int i = 0; i < 1000; i++) {
            String codigo = CodigoRecuperacao.gerar();
            assertTrue(codigo.matches("[0-9]{10}"));
            assertTrue(CodigoRecuperacao.formatoValido(codigo));
        }
    }

    @Test
    void rejeitaCodigoMalformadoSemExplicitarSegredo() {
        assertFalse(CodigoRecuperacao.formatoValido(null));
        assertFalse(CodigoRecuperacao.formatoValido("AAAA"));
        assertFalse(CodigoRecuperacao.formatoValido("IIII-0000-1111-OOOO"));
        assertEquals("0123456789",
                CodigoRecuperacao.normalizar("01234-56789"));
    }
}
