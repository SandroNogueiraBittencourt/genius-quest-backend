package br.com.geniusquest.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaginacaoTest {
    @Test
    void aceitaLimitesDeTamanhoEPreservaOrdenacaoDoChamador() {
        var ordenacao = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.asc("id"));
        assertEquals(1, Paginacao.criar(0, 1, ordenacao).getPageSize());
        var pagina = Paginacao.criar(2, 100, ordenacao);
        assertEquals(200, pagina.getOffset());
        assertEquals(ordenacao, pagina.getSort());
    }

    @Test
    void informaBadRequestQuandoTamanhoUltrapassaOLimite() {
        var erro = assertThrows(ResponseStatusException.class, () -> Paginacao.criar(0, 101, Sort.unsorted()));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
    }

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
