package br.com.geniusquest.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioDetailsService usuarioDetailsService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {

        SecurityContextHolder.clearContext();

        filter = new JwtAuthenticationFilter(
                jwtService,
                usuarioDetailsService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveIgnorarRequisicaoSemToken()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        MockFilterChain chain =
                new MockFilterChain();

        filter.doFilter(
                request,
                response,
                chain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verifyNoInteractions(jwtService);
        verifyNoInteractions(usuarioDetailsService);
    }

    @Test
    void deveAutenticarQuandoTokenForValido()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer token-valido"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        MockFilterChain chain =
                new MockFilterChain();

        when(jwtService.tokenValido("token-valido"))
                .thenReturn(true);

        when(jwtService.extrairEmail("token-valido"))
                .thenReturn(
                        "login.teste@geniusquest.com"
                );

        UserDetails usuario =
                User.withUsername(
                                "login.teste@geniusquest.com"
                        )
                        .password("senha-hash")
                        .roles("USUARIO")
                        .build();

        when(
                usuarioDetailsService.loadUserByUsername(
                        "login.teste@geniusquest.com"
                )
        ).thenReturn(usuario);

        filter.doFilter(
                request,
                response,
                chain
        );

        assertNotNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        assertTrue(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .isAuthenticated()
        );

        assertEquals(
                "login.teste@geniusquest.com",
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName()
        );

        verify(jwtService)
                .tokenValido("token-valido");

        verify(jwtService)
                .extrairEmail("token-valido");

        verify(usuarioDetailsService)
                .loadUserByUsername(
                        "login.teste@geniusquest.com"
                );
    }

    @Test
    void naoDeveAutenticarQuandoTokenForInvalido()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer token-invalido"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        MockFilterChain chain =
                new MockFilterChain();

        when(jwtService.tokenValido("token-invalido"))
                .thenReturn(false);

        filter.doFilter(
                request,
                response,
                chain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(jwtService)
                .tokenValido("token-invalido");

        verify(jwtService, never())
                .extrairEmail(anyString());

        verifyNoInteractions(
                usuarioDetailsService
        );
    }
}
