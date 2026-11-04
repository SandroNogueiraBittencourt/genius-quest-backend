package br.com.geniusquest.security;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os tokens reais emitidos por POST /api/auth/login. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtLogoutIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    @Test
    void deveNegarLogoutSemAutenticacao() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeveRevogarTokenEImpedirReutilizacao() throws Exception {
        Usuario usuario = criarUsuario();
        String token = login(usuario);

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alteracao Indevida\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNaoDeveInvalidarOutroTokenDoMesmoUsuario() throws Exception {
        Usuario usuario = criarUsuario();
        String token1 = login(usuario);
        String token2 = login(usuario);
        assertNotEquals(token1, token2, "Cada login deve gerar um JWT unico");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isOk());
    }

    @Test
    void novoLoginDepoisDoLogoutDevePermitirNovaSessao() throws Exception {
        Usuario usuario = criarUsuario();
        String anterior = login(usuario);

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anterior))
                .andExpect(status().isNoContent());

        String novo = login(usuario);
        assertNotEquals(anterior, novo);

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + novo))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + anterior))
                .andExpect(status().isUnauthorized());
    }

    private Usuario criarUsuario() {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario de Logout");
        usuario.setEmail("logout-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha(encoder.encode(SENHA));
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(usuario);
    }

    private String login(Usuario usuario) throws Exception {
        String corpo = "{\"email\":\"" + usuario.getEmail() + "\",\"senha\":\"" + SENHA + "\"}";
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
