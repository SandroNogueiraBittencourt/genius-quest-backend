package br.com.geniusquest.dto.tema;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record TemaCadastroDTO(
        @NotNull UUID categoriaId,
        @NotBlank @Size(max = 120) String titulo,
        @Size(max = 500) String descricao
) {}
