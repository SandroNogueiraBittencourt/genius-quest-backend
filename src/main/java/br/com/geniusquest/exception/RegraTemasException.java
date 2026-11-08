package br.com.geniusquest.exception;

import org.springframework.http.HttpStatus;

public class RegraTemasException extends RuntimeException {
    private final HttpStatus status;

    public RegraTemasException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
