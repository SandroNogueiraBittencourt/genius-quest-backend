package br.com.geniusquest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NomeCategoriaValidoValidator
        implements ConstraintValidator<NomeCategoriaValido, String> {
    @Override
    public boolean isValid(String nome, ConstraintValidatorContext context) {
        if (nome == null) {
            // PATCH parcial aceita nome ausente; cadastro exige @NotBlank.
            return true;
        }
        int tamanho = nome.trim().length();
        return !nome.isBlank() && tamanho >= 2 && tamanho <= 80;
    }
}
