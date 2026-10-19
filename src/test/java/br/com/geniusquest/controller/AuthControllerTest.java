package br.com.geniusquest.controller;

import br.com.geniusquest.dto.auth.LoginResponseDTO;
import br.com.geniusquest.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        AuthController controller =
                new AuthController(authService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void deveRealizarLoginERetornarToken()
            throws Exception {

        LoginResponseDTO response =
                new LoginResponseDTO(
                        "jwt-token-teste",
                        "Bearer",
                        3600
                );

        when(authService.autenticar(any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "sandro@geniusquest.com",
                                          "senha": "SenhaSegura123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.token")
                                .value("jwt-token-teste")
                )
                .andExpect(
                        jsonPath("$.tipo")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.expiraEm")
                                .value(3600)
                );
    }
}
