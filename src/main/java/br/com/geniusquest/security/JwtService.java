package br.com.geniusquest.security;

import br.com.geniusquest.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}")
            long expiracaoMs) {

        this.chave = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );

        this.expiracaoMs = expiracaoMs;
    }

    public String gerarToken(Usuario usuario) {

        Instant agora = Instant.now();

        Instant expiracao =
                agora.plusMillis(expiracaoMs);

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(
                        "usuarioId",
                        usuario.getId().toString()
                )
                .claim(
                        "perfil",
                        usuario.getPerfil().name()
                )
                .issuedAt(
                        Date.from(agora)
                )
                .expiration(
                        Date.from(expiracao)
                )
                .signWith(chave)
                .compact();
    }

    public String extrairEmail(String token) {

        return extrairClaims(token)
                .getSubject();
    }

    public String extrairPerfil(String token) {

        return extrairClaims(token)
                .get(
                        "perfil",
                        String.class
                );
    }

    public String extrairUsuarioId(String token) {

        return extrairClaims(token)
                .get(
                        "usuarioId",
                        String.class
                );
    }

    public boolean tokenValido(String token) {

        try {

            extrairClaims(token);

            return true;

        } catch (JwtException |
                 IllegalArgumentException exception) {

            return false;
        }
    }

    public long getExpiracaoMs() {
        return expiracaoMs;
    }

    private Claims extrairClaims(String token) {

        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
