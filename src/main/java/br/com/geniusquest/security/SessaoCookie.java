package br.com.geniusquest.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class SessaoCookie {
    public static final String NOME = "gq_sessao";
    public static final String ROTA_RESTAURACAO = "/api/auth/sessao";
    public static final String TOKEN_VALIDADO = SessaoCookie.class.getName() + ".token";

    private SessaoCookie() {}

    public static ResponseCookie criar(String token, long segundos, boolean seguro) {
        return ResponseCookie.from(NOME, token)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(segundos))
                .build();
    }

    public static String ler(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        String token = null;
        for (Cookie cookie : cookies) {
            if (!NOME.equals(cookie.getName())) continue;
            if (token != null || cookie.getValue() == null || cookie.getValue().isBlank()) return null;
            token = cookie.getValue();
        }
        return token;
    }
}
