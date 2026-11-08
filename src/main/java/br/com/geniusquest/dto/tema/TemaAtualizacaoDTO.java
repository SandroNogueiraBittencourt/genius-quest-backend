package br.com.geniusquest.dto.tema;

import jakarta.validation.constraints.Size;

public record TemaAtualizacaoDTO(
        @Size(min = 3, max = 120) String titulo,
        @Size(max = 500) String descricao
) {}
