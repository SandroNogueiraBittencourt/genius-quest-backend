package br.com.geniusquest.dto.recuperacao_senha;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EsqueciSeanhaRequest(@NotBlank @Email String email) {
    
}
