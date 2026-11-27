package br.com.geniusquest.security;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @Value("${app.jwt.secret}") private String segredoJwt;

    @Test
    void restauraSessaoComMesmoJwtEValidadeRestante() throws Exception {
        Usuario usuario = criarUsuario();
        String corpo = """
                {"email":"%s","senha":"%s"}
                """.formatted(usuario.getEmail(), SENHA);
        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Lax"), containsString("Path=/api/auth"))))
                .andReturn().getResponse();
        String token = JsonPath.read(login.getContentAsString(), "$.token");
        int validade = JsonPath.read(login.getContentAsString(), "$.expiraEm");
        var cookie = new Cookie(SessaoCookie.NOME, token);

        mockMvc.perform(post(SessaoCookie.ROTA_RESTAURACAO).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.expiraEm", allOf(greaterThan(0), lessThanOrEqualTo(validade))));

        mockMvc.perform(get("/api/usuarios/{id}", usuario.getId()).cookie(cookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SessaoCookie.ROTA_RESTAURACAO).cookie(cookie)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("sessao=restaurar"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post(SessaoCookie.ROTA_RESTAURACAO).cookie(cookie)
                        .header(HttpHeaders.ORIGIN, "https://origem-nao-autorizada.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutApagaCookieEImpedeRestauracaoDoTokenRevogado() throws Exception {
        String token = login(criarUsuario());
        var cookie = new Cookie(SessaoCookie.NOME, token);
        mockMvc.perform(post("/api/auth/logout").cookie(cookie)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post(SessaoCookie.ROTA_RESTAURACAO).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void naoRestauraCookieExpiradoOuSessaoComCredenciaisAntigas() throws Exception {
        Usuario usuario = criarUsuario();
        String expirado = new JwtService(segredoJwt, -60_000).gerarToken(usuario);
        String antigo = login(usuario);
        usuario.incrementarVersaoCredenciais();
        usuarios.saveAndFlush(usuario);

        for (String token : new String[] {expirado, antigo}) {
            mockMvc.perform(post(SessaoCookie.ROTA_RESTAURACAO)
                            .cookie(new Cookie(SessaoCookie.NOME, token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

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
