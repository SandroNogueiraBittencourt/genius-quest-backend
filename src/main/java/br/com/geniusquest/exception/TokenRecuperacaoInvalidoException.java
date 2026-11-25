package br.com.geniusquest.exception;

public class TokenRecuperacaoInvalidoException extends RuntimeException{
    public TokenRecuperacaoInvalidoException() {
        super("Código inválido, expirado ou já utilizado. Confira o código ou solicite outro.");
    }
}
