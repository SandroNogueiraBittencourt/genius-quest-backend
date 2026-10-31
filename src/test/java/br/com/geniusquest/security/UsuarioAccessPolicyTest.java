package br.com.geniusquest.security;

import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioAccessPolicyTest {

    @Mock private UsuarioRepository repository;
    @Mock private Authentication authentication;
    @InjectMocks private UsuarioAccessPolicy policy;

    @Test
    void permiteAcessoAoProprioPerfilAtivo() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, true);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("titular@geniusquest.test");
        when(repository.findByEmailIgnoreCase("titular@geniusquest.test"))
                .thenReturn(Optional.of(usuario));

        assertTrue(policy.isOwner(id, authentication));
    }

    @Test
    void recusaAcessoAIdentificadorDeOutroUsuario() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("titular@geniusquest.test");
        when(repository.findByEmailIgnoreCase("titular@geniusquest.test"))
                .thenReturn(Optional.of(usuario(UUID.randomUUID(), true)));

        assertFalse(policy.isOwner(UUID.randomUUID(), authentication));
    }

    @Test
    void recusaUsuarioInativo() {
        UUID id = UUID.randomUUID();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("titular@geniusquest.test");
        when(repository.findByEmailIgnoreCase("titular@geniusquest.test"))
                .thenReturn(Optional.of(usuario(id, false)));

        assertFalse(policy.isOwner(id, authentication));
    }

    @Test
    void recusaUsuarioSemAutenticacao() {
        assertFalse(policy.isOwner(UUID.randomUUID(), null));
        assertFalse(policy.isOwner(null, authentication));
        verifyNoInteractions(repository);
    }

    private static Usuario usuario(UUID id, boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setAtivo(ativo);
        return usuario;
    }
}
