package br.com.geniusquest.service;

import br.com.geniusquest.dto.auth.LoginRequestDTO;
import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import br.com.geniusquest.security.JwtService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
    }

    public LoginResponseDTO autenticar(LoginRequestDTO dto) {

        String email = dto.email()
                .trim()
                .toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        dto.senha()
                )
        );

        Usuario usuario = usuarioRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow();

        String token =
                jwtService.gerarToken(usuario);

        return new LoginResponseDTO(
                token,
                "Bearer",
                jwtService.getExpiracaoMs() / 1000
        );
    }
}
