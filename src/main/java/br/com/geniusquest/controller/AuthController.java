package br.com.geniusquest.controller;

import br.com.geniusquest.dto.auth.LoginRequestDTO;
import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
