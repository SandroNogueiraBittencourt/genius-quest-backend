package br.com.geniusquest.dto.recuperacao_senha;

import br.com.geniusquest.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
    @NotBlank @Size(min = 16, max = 24) String token,
    @NotBlank(message = "A senha é obrigatória") @SenhaForte String novaSenha) {

}
