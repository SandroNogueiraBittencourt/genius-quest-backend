package br.com.geniusquest.validation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidadorPerguntaTest {
    private final ValidadorPergunta validador = new ValidadorPergunta();
    private final String enunciado = "Qual planeta é conhecido como planeta vermelho?";
    private final List<String> alternativas = List.of("Marte", "Vênus", "Júpiter", "Saturno");

    @Test
    void validaPerguntaComQuatroAlternativas() {
        var ok = validador.validar(enunciado, alternativas, 0);
        assertEquals(4, ok.alternativas().size());
        assertEquals(0, ok.respostaCorretaIndice());
        assertEquals("qual planeta e conhecido como planeta vermelho?", ok.enunciadoNormalizado());
    }

    @Test
    void rejeitaMenosDeQuatroAlternativas() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(enunciado, List.of("Marte", "Terra"), 0));
    }

    @Test
    void rejeitaAlternativasIguaisSemDiferenciarAcentoOuCaixa() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(enunciado,
                        List.of("África", "africa", "Europa", "Ásia"), 0));
    }

    @Test
    void rejeitaIndiceInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(enunciado, alternativas, 4));
    }

    @Test
    void rejeitaEnunciadoVazio() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar("   ", alternativas, 0));
    }

    @Test
    void rejeitaAlternativaVazia() {
        assertThrows(IllegalArgumentException.class,
                () -> validador.validar(enunciado, List.of("Marte", "", "Júpiter", "Saturno"), 0));
    }
}
