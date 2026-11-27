package br.com.geniusquest.controller;

import br.com.geniusquest.dto.auth.LoginRequestDTO;
import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.service.AuthService;
import br.com.geniusquest.security.TokenRevocationService;
import br.com.geniusquest.security.JwtService;
import br.com.geniusquest.security.SessaoCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(
        name = "Autenticação",
        description = "Endpoints responsáveis pela autenticação de usuários"
)
public class AuthController {

    private final AuthService authService;
    private final TokenRevocationService tokenRevocationService;
    private final JwtService jwtService;
    private final boolean cookieSeguro;

    public AuthController(AuthService authService,
                          TokenRevocationService tokenRevocationService,
                          JwtService jwtService,
                          @Value("${app.auth.session-cookie.secure:true}") boolean cookieSeguro) {
        this.authService = authService;
        this.tokenRevocationService = tokenRevocationService;
        this.jwtService = jwtService;
        this.cookieSeguro = cookieSeguro;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Realizar login",
            description = "Autentica o usuário por e-mail e senha e retorna um token JWT"
    )
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto) {

        LoginResponseDTO sessao = authService.autenticar(dto);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        SessaoCookie.criar(sessao.token(), sessao.expiraEm(), cookieSeguro).toString())
                .body(sessao);
    }

    @PostMapping(value = "/sessao", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SessaoRestaurada> restaurarSessao(HttpServletRequest request) {
        String token = (String) request.getAttribute(SessaoCookie.TOKEN_VALIDADO);
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            long restante = Duration.between(Instant.now(), jwtService.extrairExpiracao(token)).toSeconds();
            if (restante <= 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new SessaoRestaurada(token, "Bearer", restante, jwtService.extrairEmail(token)));
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    public record SessaoRestaurada(String token, String tipo, long expiraEm, String email) {
        @Override
        public String toString() { return "SessaoRestaurada[redigida]"; }
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerrar a sessão atual",
            description = "Revoga o token Bearer informado no cabeçalho Authorization")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) {

        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authorization.substring(7);
        tokenRevocationService.revogar(token);
        var resposta = ResponseEntity.noContent();
        String cookie = SessaoCookie.ler(request);
        if (cookie == null || token.equals(cookie)) {
            resposta.header(HttpHeaders.SET_COOKIE, SessaoCookie.criar("", 0, cookieSeguro).toString());
        }
        return resposta.build();
    }
}
