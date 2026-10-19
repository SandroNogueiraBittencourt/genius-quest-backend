package br.com.geniusquest.dto.categoria;

import java.time.Instant;
import java.util.UUID;

public record CategoriaResponseDTO(
        UUID id,
        String nome,
        String descricao,
        Boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {

}
