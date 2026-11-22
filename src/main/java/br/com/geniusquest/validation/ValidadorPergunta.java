package br.com.geniusquest.validation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Barreiras determinísticas para entrada manual e futuras respostas do provedor de IA. */
public class ValidadorPergunta {
    public PerguntaConteudoValidado validar(
            String enunciado, List<String> alternativas, Integer respostaCorretaIndice) {
        String limpo = limpar(enunciado);
        if (limpo.length() < 15 || limpo.length() > 500) {
            throw new IllegalArgumentException("O enunciado deve ter entre 15 e 500 caracteres");
        }
        if (alternativas == null || alternativas.size() != 4) {
            throw new IllegalArgumentException("A pergunta deve ter exatamente quatro alternativas");
        }
        if (respostaCorretaIndice == null || respostaCorretaIndice < 0
                || respostaCorretaIndice >= alternativas.size()) {
            throw new IllegalArgumentException("Índice da alternativa correta inválido");
        }

        List<String> limpas = new ArrayList<>();
        Set<String> distintas = new HashSet<>();
        for (String alternativa : alternativas) {
            String texto = limpar(alternativa);
            if (texto.isBlank() || texto.length() > 250) {
                throw new IllegalArgumentException("Alternativa vazia ou com mais de 250 caracteres");
            }
            if (!distintas.add(normalizar(texto))) {
                throw new IllegalArgumentException("Alternativas repetidas");
            }
            limpas.add(texto);
        }
        return new PerguntaConteudoValidado(limpo, normalizar(limpo),
                List.copyOf(limpas), respostaCorretaIndice);
    }

    private String limpar(String texto) {
        return texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
    }

    public String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(limpar(texto), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcentos.toLowerCase(Locale.ROOT);
    }
}
