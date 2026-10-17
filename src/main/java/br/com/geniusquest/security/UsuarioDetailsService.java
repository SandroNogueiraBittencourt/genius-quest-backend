package br.com.geniusquest.security;

import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioDetailsService
        implements UserDetailsService {

    private final UsuarioRepository repository;

    public UsuarioDetailsService(
            UsuarioRepository repository) {

        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(
            String email)
            throws UsernameNotFoundException {

        Usuario usuario =
                repository.findByEmailIgnoreCase(email)
                        .orElseThrow(() ->
                                new UsernameNotFoundException(
                                        "Usuário não encontrado"
                                )
                        );

        return new User(
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getAtivo(),
                true,
                true,
                true,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + usuario.getPerfil().name()
                        )
                )
        );
    }
}
