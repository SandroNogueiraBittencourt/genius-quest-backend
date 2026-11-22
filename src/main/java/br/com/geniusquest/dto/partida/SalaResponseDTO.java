package br.com.geniusquest.dto.partida;

import br.com.geniusquest.entity.StatusPartida;

import java.util.List;
import java.util.UUID;

public record SalaResponseDTO(UUID codigo, UUID partidaId, UUID anfitriaoId,
        StatusPartida status, List<Participante> participantes, UUID temaId) {

    public record Participante(UUID id, String nome) {}
}
