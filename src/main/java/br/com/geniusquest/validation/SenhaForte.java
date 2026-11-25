package br.com.geniusquest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = SenhaForte.Validador.class)
public @interface SenhaForte {
    String message() default "Use pelo menos 8 caracteres, com maiúscula, minúscula, número e símbolo.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    final class Validador implements ConstraintValidator<SenhaForte, String> {
        private static final Pattern FORMATO = Pattern.compile(
                "(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\p{Nd})(?=.*[\\p{P}\\p{S}]).{8,}",
                Pattern.DOTALL);

        @Override
        public boolean isValid(String senha, ConstraintValidatorContext contexto) {
            if (senha == null || senha.isBlank()) return true;
            if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
                contexto.disableDefaultConstraintViolation();
                contexto.buildConstraintViolationWithTemplate(
                        "A senha excede o limite de 72 bytes. Use menos caracteres.")
                        .addConstraintViolation();
                return false;
            }
            return FORMATO.matcher(senha).matches();
        }
    }
}
