package br.com.geniusquest.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SessaoCookieTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mantemEscopoEProtecoesDoCookie(boolean seguro) {
        var cookie = SessaoCookie.criar("token-teste", 90, seguro);

        assertEquals(SessaoCookie.NOME, cookie.getName());
        assertEquals("token-teste", cookie.getValue());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals(Duration.ofSeconds(90), cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertEquals(seguro, cookie.isSecure());
    }

    @Test
    void cookieDeRemocaoUsaOMesmoCaminhoEExpiraImediatamente() {
        var cookie = SessaoCookie.criar("", 0, true);

        assertEquals(Duration.ZERO, cookie.getMaxAge());
        assertEquals(SessaoCookie.criar("token-teste", 90, true).getPath(), cookie.getPath());
    }

    @Test
    void procuraCookieDeSessaoEntreOutrosCookies() {
        var pedido = new MockHttpServletRequest();
        pedido.setCookies(new Cookie("preferencia", "escuro"), new Cookie(SessaoCookie.NOME, "token-teste"));

        assertEquals("token-teste", SessaoCookie.ler(pedido));
    }

    @Test
    void retornaNuloQuandoCookieDeSessaoEstaAusente() {
        var pedido = new MockHttpServletRequest();
        assertNull(SessaoCookie.ler(pedido));
        pedido.setCookies(new Cookie("preferencia", "escuro"));
        assertNull(SessaoCookie.ler(pedido));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void rejeitaCookieDeSessaoVazio(String valor) {
        var pedido = new MockHttpServletRequest();
        pedido.setCookies(new Cookie(SessaoCookie.NOME, valor));

        assertNull(SessaoCookie.ler(pedido));
    }

    @Test
    void rejeitaCookiesDeSessaoDuplicados() {
        var pedido = new MockHttpServletRequest();
        pedido.setCookies(new Cookie(SessaoCookie.NOME, "primeiro"), new Cookie(SessaoCookie.NOME, "segundo"));

        assertNull(SessaoCookie.ler(pedido));
    }
}
