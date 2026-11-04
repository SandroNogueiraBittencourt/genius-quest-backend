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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita o login real, o JWT assinado e o filtro de autenticacao.
 * Nao utiliza @WithMockUser nem tokens montados manualmente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthorizationIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveExigirJwtValidoParaConsultarPerfil() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/usuarios/{id}", id))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/usuarios/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devePermitirTitularComJwtObtidoNoLogin() throws Exception {
        Usuario titular = criarUsuario(PerfilUsuario.USUARIO);
        String token = autenticar(titular);

        mockMvc.perform(get("/api/usuarios/{id}", titular.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(titular.getId().toString()));

        mockMvc.perform(patch("/api/usuarios/{id}", titular.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Titular Atualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Titular Atualizado"));

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveBloquearJwtDoTitularAoAcessarOutroPerfil() throws Exception {
        Usuario titular = criarUsuario(PerfilUsuario.USUARIO);
        Usuario outraPessoa = criarUsuario(PerfilUsuario.USUARIO);
        String token = autenticar(titular);

        mockMvc.perform(get("/api/usuarios/{id}", outraPessoa.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/usuarios/{id}", outraPessoa.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Alteracao Indevida\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void devePermitirAdministradorComJwtReal() throws Exception {
        Usuario administrador = criarUsuario(PerfilUsuario.ADMIN);
        Usuario outraPessoa = criarUsuario(PerfilUsuario.USUARIO);
        String token = autenticar(administrador);

        mockMvc.perform(get("/api/usuarios/{id}", outraPessoa.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/{id}", outraPessoa.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Atualizado Pelo Admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Atualizado Pelo Admin"));

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deveRejeitarJwtDeUsuarioDesativado() throws Exception {
        Usuario titular = criarUsuario(PerfilUsuario.USUARIO);
        String token = autenticar(titular);

        titular.setAtivo(false);
        usuarioRepository.saveAndFlush(titular);

        mockMvc.perform(get("/api/usuarios/{id}", titular.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private Usuario criarUsuario(PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuario JWT");
        usuario.setEmail("jwt-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private String autenticar(Usuario usuario) throws Exception {
        String json = "{\"email\":\"" + usuario.getEmail()
                + "\",\"senha\":\"" + SENHA + "\"}";

        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(resposta, "$.token");
    }
}
