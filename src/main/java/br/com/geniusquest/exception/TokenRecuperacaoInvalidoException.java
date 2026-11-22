package br.com.geniusquest.exception;

public class TokenRecuperacaoInvalidoException extends RuntimeException{
    public TokenRecuperacaoInvalidoException() {
        super("Link de recuperação inválido ou expirado.");
    }
}
