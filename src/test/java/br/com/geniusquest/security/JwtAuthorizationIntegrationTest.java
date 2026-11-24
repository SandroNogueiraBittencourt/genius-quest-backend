package br.com.geniusquest.security;

import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.repository.UsuarioRepository;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;
import br.com.geniusquest.entity.TokenRecuperacaoSenha;
import br.com.geniusquest.service.CodigoRecuperacao;
import br.com.geniusquest.service.RecuperacaoSenhaService;
import java.time.Instant;
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

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository;

    @Autowired
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Autowired
    private CodigoRecuperacao codigos;

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

    @Test
    void redefinicaoDeSenhaRevogaJwtAntigoEPermiteNovoLogin() throws Exception {
        Usuario titular = criarUsuario(PerfilUsuario.USUARIO);
        String jwtAnterior = autenticar(titular);
        org.junit.jupiter.api.Assertions.assertEquals(0L, jwtService.extrairVersaoCredenciais(jwtAnterior));
        mockMvc.perform(get("/api/usuarios/{id}", titular.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAnterior))
                .andExpect(status().isOk());

        String tokenPuro = CodigoRecuperacao.gerar();
        String hash = codigos.hash("recuperacao", tokenPuro);
        TokenRecuperacaoSenha recuperacao = new TokenRecuperacaoSenha();
        recuperacao.setUsuario(titular);
        recuperacao.setTokenHash(hash);
        recuperacao.setExpiraEm(Instant.now().plusSeconds(1800));
        recuperacao.setUsado(false);
        tokenRecuperacaoSenhaRepository.saveAndFlush(recuperacao);

        recuperacaoSenhaService.redefinir(tokenPuro, "SenhaNovaTeste123!", "127.0.0.1");
        usuarioRepository.flush();

        mockMvc.perform(get("/api/usuarios/{id}", titular.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAnterior))
                .andExpect(status().isUnauthorized());

        String json = "{\"email\":\"" + titular.getEmail()
                + "\",\"senha\":\"SenhaNovaTeste123!\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
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
