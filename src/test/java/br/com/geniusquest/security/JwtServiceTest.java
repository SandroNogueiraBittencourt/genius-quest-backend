package br.com.geniusquest.security;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {

        String secret =
                "c2VncmVkby1qd3QtZ2VuaXVzLXF1ZXN0LTEyMzQ1Njc4OTA=";

        jwtService =
                new JwtService(
                        secret,
                        3600000
                );

        usuario = new Usuario();

        usuario.setId(
                UUID.randomUUID()
        );

        usuario.setNome(
                "Sandro Nogueira"
        );

        usuario.setEmail(
                "sandro@geniusquest.com"
        );

        usuario.setPerfil(
                PerfilUsuario.USUARIO
        );
    }

    @Test
    void deveGerarTokenValido() {

        String token =
                jwtService.gerarToken(usuario);

        assertNotNull(token);

        assertFalse(
                token.isBlank()
        );

        assertTrue(
                jwtService.tokenValido(token)
        );
    }

    @Test
    void deveExtrairEmailDoToken() {

        String token =
                jwtService.gerarToken(usuario);

        String email =
                jwtService.extrairEmail(token);

        assertEquals(
                "sandro@geniusquest.com",
                email
        );
    }

    @Test
    void deveExtrairPerfilDoToken() {

        String token =
                jwtService.gerarToken(usuario);

        String perfil =
                jwtService.extrairPerfil(token);

        assertEquals(
                "USUARIO",
                perfil
        );
    }

    @Test
    void deveExtrairUsuarioIdDoToken() {

        String token =
                jwtService.gerarToken(usuario);

        String usuarioId =
                jwtService.extrairUsuarioId(token);

        assertEquals(
                usuario.getId().toString(),
                usuarioId
        );
    }

    @Test
    void deveRejeitarTokenInvalido() {

        boolean valido =
                jwtService.tokenValido(
                        "token.invalido.qualquer"
                );

        assertFalse(valido);
    }
}
