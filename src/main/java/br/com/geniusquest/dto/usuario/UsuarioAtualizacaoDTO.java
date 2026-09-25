package br.com.geniusquest.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UsuarioAtualizacaoDTO(

        @Size(
                min = 3,
                max = 100,
                message = "O nome deve possuir entre 3 e 100 caracteres"
        )
        String nome,

        @Email(message = "Informe um e-mail válido")
        @Size(
                max = 150,
                message = "O e-mail deve possuir no máximo 150 caracteres"
        )
        String email

) {
}
