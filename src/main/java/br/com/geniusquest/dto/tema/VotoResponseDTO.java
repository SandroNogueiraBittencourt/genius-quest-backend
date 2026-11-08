package br.com.geniusquest.dto.tema;

import java.time.Instant;
import java.util.UUID;

public record VotoResponseDTO(UUID id, UUID temaId, UUID cicloId, Instant criadoEm) {}
