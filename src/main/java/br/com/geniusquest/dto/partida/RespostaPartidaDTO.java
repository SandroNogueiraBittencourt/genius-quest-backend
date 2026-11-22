package br.com.geniusquest.dto.partida;

import br.com.geniusquest.entity.StatusPartida;

public record RespostaPartidaDTO(int perguntaRespondida, StatusPartida status, Integer perguntaAtual) {}
