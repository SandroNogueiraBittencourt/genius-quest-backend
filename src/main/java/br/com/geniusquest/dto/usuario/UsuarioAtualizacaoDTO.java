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
                min = 1,
                max = 150,
                message = "O e-mail deve possuir entre 1 e 150 caracteres"
        )
        String email,

        @Size(max = 72)
        String senhaAtual

) {
    public UsuarioAtualizacaoDTO(String nome, String email) {
        this(nome, email, null);
    }

    @Override
    public String toString() {
        return "UsuarioAtualizacaoDTO[redigido]";
    }
}
