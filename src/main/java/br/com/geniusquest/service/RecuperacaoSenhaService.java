package br.com.geniusquest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.geniusquest.entity.TokenRecuperacaoSenha;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.TokenRecuperacaoInvalidoException;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;
import br.com.geniusquest.repository.UsuarioRepository;
import jakarta.transaction.Transactional;

@Service
public class RecuperacaoSenhaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VALIDADE_MINUTOS = 30;

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository,
                                   TokenRecuperacaoSenhaRepository tokenRepository,
                                   EmailService emailService,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.frontend-url}") String frontendUrl) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void solicitar(String email) {
        // Nunca revela se o e-mail existe: o controller responde igual nos dois casos
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::getAtivo)
                .ifPresent(usuario -> {
                    tokenRepository.invalidarTokensAbertos(usuario.getId());

                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

                    TokenRecuperacaoSenha t = new TokenRecuperacaoSenha();
                    t.setUsuario(usuario);
                    t.setTokenHash(hash(token));
                    t.setExpiraEm(Instant.now().plus(VALIDADE_MINUTOS, ChronoUnit.MINUTES));
                    tokenRepository.save(t);

                    String link = frontendUrl + "/redefinir-senha?token=" + token;
                    emailService.enviarLinkRecuperacao(usuario.getEmail(), usuario.getNome(), link);
                });
    }

    @Transactional
    public void redefinir(String token, String novaSenha) {
        TokenRecuperacaoSenha t = tokenRepository.findByTokenHash(hash(token))
                .filter(x -> !x.getUsado() && !x.expirado())
                .orElseThrow(TokenRecuperacaoInvalidoException::new);

        Usuario usuario = t.getUsuario();
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        t.setUsado(true);
        tokenRepository.save(t);
    }

    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}   
