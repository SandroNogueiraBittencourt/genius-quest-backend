package br.com.geniusquest.dto.partida;

import br.com.geniusquest.entity.StatusPartida;
import java.time.Instant;
import java.util.UUID;

public record PartidaResponseDTO(
        UUID id, UUID cicloId, String cicloNome, UUID temaId,
        String temaTitulo, String categoriaNome, StatusPartida status,
        Instant criadoEm
) {}
