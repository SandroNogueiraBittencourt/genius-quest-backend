package br.com.geniusquest.dto.tema;

import br.com.geniusquest.entity.StatusTema;
import java.time.Instant;
import java.util.UUID;

public record TemaResponseDTO(
        UUID id, UUID categoriaId, UUID autorId, String titulo,
        String descricao, StatusTema status, Instant criadoEm, Instant atualizadoEm
) {}
