package br.com.geniusquest.security;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock
    private UsuarioRepository repository;

    @InjectMocks
    private UsuarioDetailsService service;

    @Test
    void deveCarregarUsuarioPorEmail() {

        Usuario usuario = new Usuario();

        usuario.setId(UUID.randomUUID());
        usuario.setNome("Sandro Nogueira");
        usuario.setEmail("sandro@geniusquest.com");
        usuario.setSenha("$2a$10$hash");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        usuario.setAtivo(true);

        when(repository.findByEmailIgnoreCase(
                "sandro@geniusquest.com"
        )).thenReturn(Optional.of(usuario));

        UserDetails userDetails =
                service.loadUserByUsername(
                        "sandro@geniusquest.com"
                );

        assertNotNull(userDetails);

        assertEquals(
                "sandro@geniusquest.com",
                userDetails.getUsername()
        );

        assertTrue(
                userDetails.getAuthorities()
                        .stream()
                        .anyMatch(
                                authority ->
                                        authority.getAuthority()
                                                .equals("ROLE_USUARIO")
                        )
        );
    }

    @Test
    void deveLancarExcecaoQuandoUsuarioNaoExistir() {

        when(repository.findByEmailIgnoreCase(
                "inexistente@geniusquest.com"
        )).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "inexistente@geniusquest.com"
                )
        );
    }
}
