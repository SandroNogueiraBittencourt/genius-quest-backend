package br.com.geniusquest.dto.usuario;

import br.com.geniusquest.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
        @NotBlank(message = "Informe sua senha atual.") @Size(max = 72) String senhaAtual,
        @NotBlank(message = "Informe sua nova senha.") @SenhaForte String novaSenha
) {
    @Override
    public String toString() {
        return "AlterarSenhaRequest[redigido]";
    }
}
