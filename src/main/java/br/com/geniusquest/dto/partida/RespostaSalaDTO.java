package br.com.geniusquest.dto.partida;

import br.com.geniusquest.entity.StatusPartida;

public record RespostaSalaDTO(int perguntaRespondida, StatusPartida status, Integer perguntaAtual) {}
