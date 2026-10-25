package br.com.geniusquest.dto.categoria;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoriaAtualizacaoDTO(

        @Size(
                max = 80,
                message = "O nome da categoria deve possuir no máximo 80 caracteres"
        )
        @Pattern(
                regexp = ".*\\S.*",
                message = "O nome da categoria não pode estar em branco"
        )
        String nome,

        @Size(
                max = 255,
                message = "A descrição deve possuir no máximo 255 caracteres"
        )
        String descricao

) {
}
