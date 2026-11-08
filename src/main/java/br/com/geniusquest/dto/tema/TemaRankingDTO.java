package br.com.geniusquest.dto.tema;

import java.util.UUID;

public record TemaRankingDTO(
        int posicao, UUID temaId, String titulo, long votos, long votantesDistintos
) {}
