package br.com.geniusquest.dto.categoria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaCadastroDTO(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @Size(
                max = 80,
                message = "O nome da categoria deve possuir no máximo 80 caracteres"
        )
        String nome,

        @Size(
                max = 255,
                message = "A descrição deve possuir no máximo 255 caracteres"
        )
        String descricao

) {
}
