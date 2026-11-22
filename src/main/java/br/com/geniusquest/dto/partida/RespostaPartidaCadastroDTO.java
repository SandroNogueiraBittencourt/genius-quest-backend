package br.com.geniusquest.dto.partida;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RespostaPartidaCadastroDTO(
        @NotNull UUID perguntaId,
        @NotNull @Min(0) @Max(3) Integer alternativaIndice
) {}
