package br.com.geniusquest.service;

import br.com.geniusquest.dto.auth.LoginRequestDTO;
import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import br.com.geniusquest.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private AuthenticationManager authenticationManager;

        @Mock
        private UsuarioRepository usuarioRepository;

        @Mock
        private JwtService jwtService;

        @InjectMocks
        private AuthService authService;

        private Usuario usuario;

        @BeforeEach
        void setUp() {

                usuario = new Usuario();
                usuario.setId(UUID.randomUUID());
                usuario.setNome("Sandro Nogueira");
                usuario.setEmail("sandro@geniusquest.com");
                usuario.setSenha("$2a$10$hash");
                usuario.setPerfil(PerfilUsuario.USUARIO);
                usuario.setAtivo(true);
        }

        @Test
        void deveAutenticarUsuarioERetornarToken() {

                LoginRequestDTO request = new LoginRequestDTO(
                                "SANDRO@GENIUSQUEST.COM",
                                "SenhaSegura123!");

                when(usuarioRepository.findByEmailIgnoreCase(
                                "sandro@geniusquest.com")).thenReturn(Optional.of(usuario));

                when(jwtService.gerarToken(usuario))
                                .thenReturn("jwt-token-teste");

                when(jwtService.getExpiracaoMs())
                                .thenReturn(3600000L);

                LoginResponseDTO response = authService.autenticar(request);

                assertNotNull(response);

                assertEquals(
                                "jwt-token-teste",
                                response.token());

                assertEquals(
                                "Bearer",
                                response.tipo());

                assertEquals(
                                3600,
                                response.expiraEm());

                verify(authenticationManager)
                                .authenticate(
                                                any(
                                                                UsernamePasswordAuthenticationToken.class));

                verify(usuarioRepository)
                                .findByEmailIgnoreCase(
                                                "sandro@geniusquest.com");

                verify(jwtService)
                                .gerarToken(usuario);
        }

        @Test
        void naoDeveGerarTokenQuandoCredenciaisForemInvalidas() {

                LoginRequestDTO request = new LoginRequestDTO(
                                "sandro@geniusquest.com",
                                "senha-errada");

                when(authenticationManager.authenticate(any()))
                                .thenThrow(
                                                new org.springframework.security.authentication.BadCredentialsException(
                                                                "Credenciais inválidas"));

                assertThrows(
                                org.springframework.security.authentication.BadCredentialsException.class,
                                () -> authService.autenticar(request));

                verify(usuarioRepository, never())
                                .findByEmailIgnoreCase(any());

                verify(jwtService, never())
                                .gerarToken(any());
        }
}
