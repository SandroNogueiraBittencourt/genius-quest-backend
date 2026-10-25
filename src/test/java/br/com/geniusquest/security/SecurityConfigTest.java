package br.com.geniusquest.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornar401QuandoNaoAutenticado()
            throws Exception {

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

    @Test
    void deveRetornar401AoCadastrarCategoriaSemAutenticacao()
            throws Exception {

        mockMvc.perform(
                post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Categoria sem autenticação",
                                  "descricao": "Teste de segurança"
                                }
                                """)
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
    void devePermitirUsuarioAutenticadoCadastrarCategoria()
            throws Exception {

        String nomeCategoria =
                "Categoria-" + UUID.randomUUID();

        String json = """
                {
                  "nome": "%s",
                  "descricao": "Categoria criada pelo teste de segurança"
                }
                """.formatted(nomeCategoria);

        mockMvc.perform(
                post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(
                status().isCreated()
        );
    }

    @Test
    @WithMockUser(
            username = "usuario@geniusquest.com",
            roles = "USUARIO"
    )
    void deveRetornar403QuandoUsuarioTentarListarPendentes()
            throws Exception {

        mockMvc.perform(
                get("/api/categorias/pendentes")
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
    void devePermitirAdminListarPendentes()
            throws Exception {

        mockMvc.perform(
                get("/api/categorias/pendentes")
        )
        .andExpect(
                status().isOk()
        );
    }

    @Test
    @WithMockUser(
            username = "usuario@geniusquest.com",
            roles = "USUARIO"
    )
    void deveRetornar403QuandoUsuarioTentarAprovarCategoria()
            throws Exception {

        mockMvc.perform(
                patch(
                        "/api/categorias/{id}/aprovar",
                        UUID.randomUUID()
                )
        )
        .andExpect(
                status().isForbidden()
        );
    }

    @Test
    @WithMockUser(
            username = "usuario@geniusquest.com",
            roles = "USUARIO"
    )
    void deveRetornar403QuandoUsuarioTentarRemoverCategoria()
            throws Exception {

        mockMvc.perform(
                patch(
                        "/api/categorias/{id}/remover",
                        UUID.randomUUID()
                )
        )
        .andExpect(
                status().isForbidden()
        );
    }
}