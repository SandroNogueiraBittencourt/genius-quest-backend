package br.com.geniusquest.dto.pergunta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Geracao paga depende de dois feature flags; nunca aprova automaticamente. */
public record PedidoGeracaoIADTO(
        @NotNull UUID temaId,
        @NotNull @Min(1) @Max(3) Integer quantidade) {
}
