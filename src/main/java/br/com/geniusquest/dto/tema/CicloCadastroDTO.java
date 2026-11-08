package br.com.geniusquest.dto.tema;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CicloCadastroDTO(
        @NotBlank @Size(max = 100) String nome,
        @NotNull Instant inicioEm,
        @NotNull Instant fimEm
) {}
