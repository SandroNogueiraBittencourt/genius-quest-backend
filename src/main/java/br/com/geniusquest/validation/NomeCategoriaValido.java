package br.com.geniusquest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NomeCategoriaValidoValidator.class)
public @interface NomeCategoriaValido {
    String message() default "O nome da categoria deve possuir entre 2 e 80 caracteres";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
