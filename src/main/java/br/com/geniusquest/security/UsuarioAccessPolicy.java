package br.com.geniusquest.security;

import br.com.geniusquest.repository.UsuarioRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Verifica a titularidade usando a identidade autenticada, nunca dados enviados pelo cliente. */
@Component("usuarioAccessPolicy")
public class UsuarioAccessPolicy {

    private final UsuarioRepository usuarios;

    public UsuarioAccessPolicy(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public boolean isOwner(UUID usuarioId, Authentication authentication) {
        if (usuarioId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return usuarios.findByEmailIgnoreCase(authentication.getName())
                .filter(usuario -> Boolean.TRUE.equals(usuario.getAtivo()))
                .map(usuario -> usuario.getId().equals(usuarioId))
                .orElse(false);
    }
}
