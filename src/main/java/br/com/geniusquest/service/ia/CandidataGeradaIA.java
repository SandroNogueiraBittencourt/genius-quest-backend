package br.com.geniusquest.service.ia;

import br.com.geniusquest.entity.DificuldadePergunta;
import java.util.List;

/** Dados não confiáveis vindos da IA, a serem validados no servidor. */
public record CandidataGeradaIA(String enunciado, List<String> alternativas,
        Integer respostaCorretaIndice, DificuldadePergunta dificuldade,
        String fonteReferencia) { }
