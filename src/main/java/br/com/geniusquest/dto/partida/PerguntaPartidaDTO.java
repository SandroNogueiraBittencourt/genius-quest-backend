package br.com.geniusquest.dto.partida;

import java.util.List;
import java.util.UUID;

public record PerguntaPartidaDTO(UUID partidaId, int numero, int totalPerguntas,
        String enunciado, List<String> alternativas) {}
