package br.com.geniusquest.dto.partida;

import java.time.Instant;
import java.util.UUID;

/** Confirma a resposta registrada, sem revelar acerto ou gabarito antes do fechamento do quiz. */
public record RespostaPartidaDTO(
        UUID perguntaId,
        int alternativaIndice,
        Instant respondidaEm
) {}
