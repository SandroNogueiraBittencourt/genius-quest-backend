package br.com.geniusquest.dto.categoria;

import br.com.geniusquest.validation.NomeCategoriaValido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaCadastroDTO(

        @NotBlank(message = "O nome da categoria é obrigatório")
        @NomeCategoriaValido
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
