package br.com.geniusquest.security;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornar401QuandoNaoAutenticado() throws Exception {

        mockMvc.perform(
                get("/api/usuarios")
        )
        .andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    @WithMockUser(
            username = "usuario@geniusquest.com",
            roles = "USUARIO"
    )
    void deveRetornar403QuandoUsuarioNaoForAdmin()
            throws Exception {

        mockMvc.perform(
                get("/api/usuarios")
        )
        .andExpect(
                status().isForbidden()
        );
    }

    @Test
    @WithMockUser(
            username = "admin@geniusquest.com",
            roles = "ADMIN"
    )
    void devePermitirListagemParaAdmin()
            throws Exception {

        mockMvc.perform(
                get("/api/usuarios")
        )
        .andExpect(
                status().isOk()
        );
    }
}
