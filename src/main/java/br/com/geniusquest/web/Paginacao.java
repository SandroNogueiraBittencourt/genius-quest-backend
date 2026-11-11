package br.com.geniusquest.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Paginacao {
    private Paginacao() {}

    public static Pageable criar(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Paginação inválida: page deve ser >= 0 e size entre 1 e 100");
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("nome"), Sort.Order.asc("id")));
    }
}
