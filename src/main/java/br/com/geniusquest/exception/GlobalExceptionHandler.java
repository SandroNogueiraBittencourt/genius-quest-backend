package br.com.geniusquest.exception;

import br.com.geniusquest.dto.erro.ErroRespostaDTO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarEmailJaCadastrado(
            EmailJaCadastradoException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.CONFLICT;

        ErroRespostaDTO erro = new ErroRespostaDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(status)
                .body(erro);
    }

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ResponseEntity<ErroRespostaDTO> tratarUsuarioNaoEncontrado(
            UsuarioNaoEncontradoException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.NOT_FOUND;

        ErroRespostaDTO erro = new ErroRespostaDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(status)
                .body(erro);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroRespostaDTO> tratarErroValidacao(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;

        Map<String, String> campos = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError ->
                        campos.putIfAbsent(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        )
                );

        ErroRespostaDTO erro = new ErroRespostaDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Existem campos inválidos na requisição",
                request.getRequestURI(),
                campos
        );

        return ResponseEntity
                .status(status)
                .body(erro);
    }
}
