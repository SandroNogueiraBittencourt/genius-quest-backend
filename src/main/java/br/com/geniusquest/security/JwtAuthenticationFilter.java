package br.com.geniusquest.security;

import java.io.IOException;
import br.com.geniusquest.repository.UsuarioRepository;
import br.com.geniusquest.entity.Usuario;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final TokenRevocationService tokenRevocationService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioDetailsService usuarioDetailsService,
            TokenRevocationService tokenRevocationService,
            UsuarioRepository usuarioRepository) {

        this.jwtService = jwtService;
        this.usuarioDetailsService = usuarioDetailsService;
        this.tokenRevocationService = tokenRevocationService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        boolean restaurarSessao = "POST".equals(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + SessaoCookie.ROTA_RESTAURACAO);
        String authorizationHeader = request.getHeader("Authorization");
        String token = restaurarSessao ? SessaoCookie.ler(request)
                : authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                        ? authorizationHeader.substring(7) : null;

        if (token == null) {

            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.tokenValido(token)
                || tokenRevocationService.estaRevogado(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {

            String email =
                    jwtService.extrairEmail(token);

            UserDetails usuario;
            try {
                usuario = usuarioDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException ex) {
                filterChain.doFilter(request, response);
                return;
            }

            Usuario entidade = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
            // Uma redefinicao de senha incrementa a versao no banco.
            // Nenhum JWT antigo e autorizado apos o commit.
            if (usuario.isEnabled() && entidade != null
                    && Boolean.TRUE.equals(entidade.getAtivo())
                    && entidade.getId().toString().equals(jwtService.extrairUsuarioId(token))
                    && entidade.getVersaoCredenciais()
                            == jwtService.extrairVersaoCredenciais(token)) {

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                usuario,
                                null,
                                usuario.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
                if (restaurarSessao) request.setAttribute(SessaoCookie.TOKEN_VALIDADO, token);
            }
        }

        filterChain.doFilter(request, response);
    }
}
