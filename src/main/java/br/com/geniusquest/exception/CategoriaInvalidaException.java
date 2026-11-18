package br.com.geniusquest.exception;

public class CategoriaInvalidaException extends RuntimeException{
    public CategoriaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
