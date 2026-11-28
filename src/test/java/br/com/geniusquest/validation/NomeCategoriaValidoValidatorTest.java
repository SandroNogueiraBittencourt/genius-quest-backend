package br.com.geniusquest.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NomeCategoriaValidoValidatorTest {
    private final NomeCategoriaValidoValidator validador = new NomeCategoriaValidoValidator();

    @Test
    void permiteNomeAusenteNaAtualizacaoParcial() {
        assertTrue(validador.isValid(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", " A "})
    void rejeitaNomesVaziosOuComMenosDeDoisCaracteres(String nome) {
        assertFalse(validador.isValid(nome, null));
    }

    @Test
    void medeLimitesAposRemoverEspacosNasExtremidades() {
        assertTrue(validador.isValid("  IA  ", null));
        assertTrue(validador.isValid(" " + "A".repeat(80) + " ", null));
        assertFalse(validador.isValid(" " + "A".repeat(81) + " ", null));
    }
}
