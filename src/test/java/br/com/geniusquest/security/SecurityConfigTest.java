package br.com.geniusquest.security;

import java.util.UUID;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private UsuarioRepository usuarioRepository;

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
    @Test
    void deveRetornar401AoConsultarOuAlterarPerfilSemLogin() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/usuarios/{id}", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Atualizado\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @WithMockUser(username = "titular-etapa0@geniusquest.test", roles = "USUARIO")
    void devePermitirTitularConsultarEAlterarSeuPerfil() throws Exception {
        Usuario titular = persistirUsuario("titular-etapa0@geniusquest.test");

        mockMvc.perform(get("/api/usuarios/{id}", titular.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/{id}", titular.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Atualizado\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @WithMockUser(username = "titular-etapa0@geniusquest.test", roles = "USUARIO")
    void deveProibirConsultaEAlteracaoDoPerfilDeOutraPessoa() throws Exception {
        persistirUsuario("titular-etapa0@geniusquest.test");
        Usuario outraPessoa = persistirUsuario("outra-etapa0@geniusquest.test");

        mockMvc.perform(get("/api/usuarios/{id}", outraPessoa.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/usuarios/{id}", outraPessoa.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Indevido\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin-etapa0@geniusquest.test", roles = "ADMIN")
    void devePermitirAdminConsultarEAlterarOutroPerfil() throws Exception {
        Usuario outraPessoa = persistirUsuario("outra-etapa0@geniusquest.test");

        mockMvc.perform(get("/api/usuarios/{id}", outraPessoa.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/{id}", outraPessoa.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alterado Pelo Admin\"}"))
                .andExpect(status().isOk());
    }

    private Usuario persistirUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de Teste");
        usuario.setEmail(email);
        usuario.setSenha("hash-inutilizado-por-mock-user");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarioRepository.saveAndFlush(usuario);
    }

}