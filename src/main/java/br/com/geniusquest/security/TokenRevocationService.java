package br.com.geniusquest.security;

import br.com.geniusquest.repository.TokenRevogadoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TokenRevocationService {

    private final TokenRevogadoRepository repository;
    private final JwtService jwtService;

    public TokenRevocationService(TokenRevogadoRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public boolean estaRevogado(String token) {
        return repository.existe(hash(token));
    }

    @Transactional
    public void revogar(String token) {
        if (!jwtService.tokenValido(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }

        Instant expiracao = jwtService.extrairExpiracao(token);
        repository.registrar(hash(token), expiracao);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
