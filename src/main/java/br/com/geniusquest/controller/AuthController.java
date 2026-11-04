package br.com.geniusquest.controller;

import br.com.geniusquest.dto.auth.LoginRequestDTO;
import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.service.AuthService;
import br.com.geniusquest.security.TokenRevocationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    public AuthController(AuthService authService,
                          TokenRevocationService tokenRevocationService) {
        this.authService = authService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Realizar login",
            description = "Autentica o usuário por e-mail e senha e retorna um token JWT"
    )
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto) {

        return ResponseEntity.ok(
                authService.autenticar(dto)
        );
    }
    @PostMapping("/logout")
    @Operation(summary = "Encerrar a sessão atual",
            description = "Revoga o token Bearer informado no cabeçalho Authorization")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        tokenRevocationService.revogar(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
