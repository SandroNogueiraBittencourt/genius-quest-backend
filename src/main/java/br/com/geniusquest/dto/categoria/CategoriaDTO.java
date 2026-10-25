package br.com.geniusquest.dto.categoria;

import java.time.Instant;
import java.util.UUID;

import br.com.geniusquest.entity.StatusCategoria;

public record CategoriaDTO(
        UUID id,
        String nome,
        String descricao,
        StatusCategoria status,
        Instant criadoEm,
        Instant atualizadoEm
) {

}
