package br.com.geniusquest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CodigoVerificacaoEmailTest {
    @Test
    void geraCodigoNumericoDeDezDigitos() {
        assertTrue(CodigoVerificacaoEmail.gerar().matches("[0-9]{10}"));
    }

    @Test
    void preservaZeroInicialAoNormalizarEspacosEHifens() {
        String codigo = " 012-345 6789 ";
        assertEquals("0123456789", CodigoVerificacaoEmail.normalizar(codigo));
        assertTrue(CodigoVerificacaoEmail.formatoValido(codigo));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456789", "12345678901", "123456789A", "12345.67890", "１２３４５６７８９０"})
    void rejeitaCodigosForaDoFormato(String codigo) {
        assertFalse(CodigoVerificacaoEmail.formatoValido(codigo));
    }

    @Test
    void calculaOMesmoHashParaCodigoFormatado() {
        var codigos = codigosComChave((byte) 1);
        String hash = codigos.hash("verificacao", "0123456789");

        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, codigos.hash("verificacao", "012-345 6789"));
    }

    @Test
    void hashDistingueFinalidadeCodigoEChave() {
        var codigos = codigosComChave((byte) 1);
        String hash = codigos.hash("verificacao", "0123456789");

        assertNotEquals(hash, codigos.hash("outra-finalidade", "0123456789"));
        assertNotEquals(hash, codigos.hash("verificacao", "0123456788"));
        assertNotEquals(hash, codigosComChave((byte) 2).hash("verificacao", "0123456789"));
    }

    private CodigoVerificacaoEmail codigosComChave(byte valor) {
        byte[] chave = new byte[32];
        Arrays.fill(chave, valor);
        return new CodigoVerificacaoEmail(Base64.getEncoder().encodeToString(chave));
    }
}
