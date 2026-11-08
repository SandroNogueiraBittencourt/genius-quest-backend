package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TemaVotacaoIntegrationTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired VotoTemaRepository votos;

    private Usuario jogador;
    private Usuario segundo;
    private Categoria categoria;
    private Tema tema;

    @BeforeEach
    void preparar() {
        jogador = usuario("jogador@geniusquest.test");
        segundo = usuario("segundo@geniusquest.test");
        categoria = new Categoria();
        categoria.setNome("Conhecimento");
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);
        tema = novoTema("Astronomia", StatusTema.PENDENTE);
    }

    private Usuario usuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Pessoa Teste");
        usuario.setEmail(email);
        usuario.setSenha("hash-de-teste-sem-login");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(usuario);
    }

    private Tema novoTema(String titulo, StatusTema status) {
        Tema novo = new Tema();
        novo.setTitulo(titulo);
        novo.setTituloNormalizado(titulo.toLowerCase(java.util.Locale.ROOT));
        novo.setAutor(jogador);
        novo.setCategoria(categoria);
        novo.setStatus(status);
        return temas.saveAndFlush(novo);
    }

    private CicloVotacao cicloAtivo() {
        CicloVotacao ciclo = new CicloVotacao();
        ciclo.setNome("Rodada Semanal");
        ciclo.setInicioEm(Instant.now().minusSeconds(60));
        ciclo.setFimEm(Instant.now().plusSeconds(3600));
        return ciclos.saveAndFlush(ciclo);
    }

    @Test
    void naoAutenticadoNaoPodeSugerirVotarOuAcessarModeracao() throws Exception {
        mvc.perform(post("/api/temas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"História\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/temas/{id}/votos", tema.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/temas/moderacao")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void jogadorNaoPodeModerarOuCriarCiclos() throws Exception {
        mvc.perform(get("/api/temas/moderacao")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/temas/{id}/aprovar", tema.getId())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/temas/{id}/remover", tema.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/api/ciclos-votacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Semana\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void sugestaoRegistraAutorEIniciaPendenteSemVazarParaListagem() throws Exception {
        mvc.perform(post("/api/temas").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        new br.com.geniusquest.dto.tema.TemaCadastroDTO(
                                categoria.getId(), "Ciências Naturais", "Exploração"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorId").value(jogador.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(header().exists("Location"));
        mvc.perform(get("/api/temas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/temas/{id}", tema.getId())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "segundo@geniusquest.test", roles = "USUARIO")
    void outroJogadorNaoConsultaTemaPendente() throws Exception {
        mvc.perform(get("/api/temas/{id}", tema.getId())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void impedeTemaDuplicadoMesmoComCaixaEEspacosDiferentes() throws Exception {
        String payload = json.writeValueAsString(
                new br.com.geniusquest.dto.tema.TemaCadastroDTO(categoria.getId(),
                        "  Temas   históricos  ", null));
        mvc.perform(post("/api/temas").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/temas").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        new br.com.geniusquest.dto.tema.TemaCadastroDTO(categoria.getId(),
                                "temas históricos", "Outro"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void temaEmCategoriaNaoAprovadaNaoPodeSerSugerido() throws Exception {
        categoria.setStatus(StatusCategoria.REMOVIDA);
        categorias.saveAndFlush(categoria);
        mvc.perform(post("/api/temas").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        new br.com.geniusquest.dto.tema.TemaCadastroDTO(categoria.getId(),
                                "Física", null))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void adminAprovaEditaESuspende() throws Exception {
        mvc.perform(patch("/api/temas/{id}", tema.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Estrelas e planetas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Estrelas e planetas"));
        mvc.perform(patch("/api/temas/{id}/aprovar", tema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"));
        mvc.perform(get("/api/temas")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/temas/{id}/suspender", tema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENSO"));
        mvc.perform(get("/api/temas")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/temas/moderacao")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void votaUmaVezPorTemaPorCicloERankingContaVoto() throws Exception {
        tema.setStatus(StatusTema.APROVADO);
        temas.saveAndFlush(tema);
        CicloVotacao ciclo = cicloAtivo();
        mvc.perform(get("/api/ciclos-votacao/atual")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ciclo.getId().toString()));
        mvc.perform(post("/api/temas/{id}/votos", tema.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cicloId").value(ciclo.getId().toString()));
        mvc.perform(post("/api/temas/{id}/votos", tema.getId()))
                .andExpect(status().isConflict());
        assertThat(votos.count()).isEqualTo(1);
        mvc.perform(get("/api/ciclos-votacao/{id}/ranking", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].temaId").value(tema.getId().toString()))
                .andExpect(jsonPath("$[0].votos").value(1))
                .andExpect(jsonPath("$[0].votantesDistintos").value(1));
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void temaSuspensoNaoAceitaVotos() throws Exception {
        tema.setStatus(StatusTema.SUSPENSO);
        temas.saveAndFlush(tema);
        cicloAtivo();
        mvc.perform(post("/api/temas/{id}/votos", tema.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void semCicloAbertoNaoAceitaVotos() throws Exception {
        tema.setStatus(StatusTema.APROVADO);
        temas.saveAndFlush(tema);
        mvc.perform(post("/api/temas/{id}/votos", tema.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void fechaCicloComVencedorPorMaiorNumeroDeVotos() throws Exception {
        tema.setStatus(StatusTema.APROVADO);
        temas.saveAndFlush(tema);
        Tema concorrente = novoTema("Botânica", StatusTema.APROVADO);
        CicloVotacao ciclo = cicloAtivo();
        mvc.perform(post("/api/temas/{id}/votos", concorrente.getId())
                .with(user("jogador@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/temas/{id}/votos", concorrente.getId())
                .with(user("segundo@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/temas/{id}/votos", tema.getId())
                .with(user("jogador@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/ciclos-votacao/{id}/encerrar", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.vencedorTemaId").value(concorrente.getId().toString()));
        mvc.perform(get("/api/ciclos-votacao/atual"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void impedeCicloSobrepostoComRestricaoNoBanco() throws Exception {
        cicloAtivo();
        String payload = json.writeValueAsString(
                new br.com.geniusquest.dto.tema.CicloCadastroDTO(
                        "Nova Semana", Instant.now(), Instant.now().plusSeconds(7200)));
        mvc.perform(post("/api/ciclos-votacao").contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void rejeitaPeriodoInvalidoNoCadastroDoCiclo() throws Exception {
        Instant agora = Instant.now();
        mvc.perform(post("/api/ciclos-votacao").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        new br.com.geniusquest.dto.tema.CicloCadastroDTO(
                                "Semana errada", agora.plusSeconds(60), agora.minusSeconds(60)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void classificaEmpatePeloTemaMaisAntigo() throws Exception {
        tema.setStatus(StatusTema.APROVADO);
        temas.saveAndFlush(tema);
        Tema novo = novoTema("Botânica", StatusTema.APROVADO);
        CicloVotacao ciclo = cicloAtivo();
        mvc.perform(post("/api/temas/{id}/votos", novo.getId())
                .with(user("jogador@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/temas/{id}/votos", tema.getId())
                .with(user("segundo@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/ciclos-votacao/{id}/ranking", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].temaId").value(tema.getId().toString()))
                .andExpect(jsonPath("$[0].votos").value(1))
                .andExpect(jsonPath("$[1].votos").value(1));
    }
}
