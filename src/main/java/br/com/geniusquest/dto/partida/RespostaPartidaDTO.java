package br.com.geniusquest.dto.partida;

import java.time.Instant;
import java.util.UUID;

/** Informa somente a correção da própria tentativa, nunca o índice do gabarito. */
public record RespostaPartidaDTO(
        UUID perguntaId,
        int alternativaIndice,
        boolean correta,
        Instant respondidaEm
) {}
