package br.com.geniusquest.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class PaginacaoTest {
    @Test
    void aceitaPaginaValidaComOrdenacaoDeterministica() {
        Pageable p = Paginacao.criar(2, 10);
        assertEquals(2, p.getPageNumber());
        assertEquals(10, p.getPageSize());
        assertEquals("nome: ASC,id: ASC", p.getSort().toString());
    }

    @Test
    void rejeitaPaginasETamanhosInvalidos() {
        assertThrows(ResponseStatusException.class, () -> Paginacao.criar(-1, 10));
        assertThrows(ResponseStatusException.class, () -> Paginacao.criar(0, 0));
        assertThrows(ResponseStatusException.class, () -> Paginacao.criar(0, 101));
    }
}
