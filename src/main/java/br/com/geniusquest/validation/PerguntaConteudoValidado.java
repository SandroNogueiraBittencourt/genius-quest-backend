package br.com.geniusquest.validation;

import java.util.List;

/** Contrato interno validado; a resposta correta só é acessível no backend. */
public record PerguntaConteudoValidado(
        String enunciado,
        String enunciadoNormalizado,
        List<String> alternativas,
        int respostaCorretaIndice) { }
