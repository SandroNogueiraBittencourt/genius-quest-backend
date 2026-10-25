package br.com.geniusquest.exception;

public class CategoriaJaCadastradaException extends RuntimeException{
    public CategoriaJaCadastradaException(){
        super("Categoria já cadastrada");
    }
}
