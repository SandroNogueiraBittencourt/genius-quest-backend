package br.com.geniusquest.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa os mapeamentos opt-in de paginacao com a SecurityFilterChain real.
 * Os usuarios simulados sao usados apenas para os testes de papeis;
 * a validacao JWT real permanece coberta por JwtAuthorizationIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginacaoSegurancaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usuarioPaginadoSemAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "0").param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void usuarioComumNaoPodeListarUsuariosPaginados() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administradorPodeListarUsuariosPaginados() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").isNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listagemLegadaDeUsuariosContinuaArray() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void paginacaoInvalidaDeUsuariosRetorna400() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("page", "-1").param("size", "10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/usuarios").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void usuarioComumNaoPodeListarCategoriasPendentesPaginadas() throws Exception {
        mockMvc.perform(get("/api/categorias/pendentes")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void usuarioComumNaoPodeListarCategoriasRemovidasPaginadas() throws Exception {
        mockMvc.perform(get("/api/categorias/removidas")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administradorPodeListarCategoriasPendentesPaginadas() throws Exception {
        mockMvc.perform(get("/api/categorias/pendentes")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.number").value(0));
    }
}
