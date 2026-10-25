package br.com.geniusquest.dto.categoria;

import br.com.geniusquest.entity.StatusCategoria;

import java.time.Instant;
import java.util.UUID;

public record CategoriaResponseDTO(
        UUID id,
        String nome,
        String descricao,
        StatusCategoria status,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
