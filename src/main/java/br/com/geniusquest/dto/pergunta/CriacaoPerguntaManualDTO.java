package br.com.geniusquest.dto.pergunta;

import br.com.geniusquest.entity.DificuldadePergunta;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Entrada administrativa para candidata manual; sempre inicia como PENDENTE. */
public record CriacaoPerguntaManualDTO(
        @NotNull UUID temaId,
        @NotBlank @Size(min = 15, max = 500) String enunciado,
        @NotNull @Size(min = 4, max = 4)
        List<@NotBlank @Size(max = 250) String> alternativas,
        @NotNull @Min(0) @Max(3) Integer respostaCorretaIndice,
        @NotNull DificuldadePergunta dificuldadeInicial,
        @Size(max = 500) String fonteReferencia) {
}
