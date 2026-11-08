package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistoricoVotacaoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired ResultadoTemaCicloRepository resultados;

    private Usuario jogador;
    private Usuario segundo;
    private Categoria categoria;
    private Tema tema;

    @BeforeEach
    void preparar() {
        jogador = usuario("jogador");
        segundo = usuario("segundo");
        categoria = new Categoria();
        categoria.setNome("Histórico " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        tema = new Tema();
        tema.setTitulo("História do Futebol");
        tema.setTituloNormalizado("história do futebol");
        tema.setCategoria(categoria);
        tema.setAutor(jogador);
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);
    }

    private Usuario usuario(String prefixo) {
        Usuario u = new Usuario();
        u.setNome("Jogador");
        u.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        u.setSenha("hash-para-teste");
        u.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(u);
    }

    private CicloVotacao novoCiclo(String nome) {
        CicloVotacao ciclo = new CicloVotacao();
        ciclo.setNome(nome);
        ciclo.setInicioEm(Instant.now().minusSeconds(60));
        ciclo.setFimEm(Instant.now().plusSeconds(3600));
        return ciclos.saveAndFlush(ciclo);
    }

    @Test
    void historicoExigeAutenticacao() throws Exception {
        mvc.perform(get("/api/ciclos-votacao/historico"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void mostraSomenteEncerradosOrdenadosEPaginados() throws Exception {
        novoCiclo("Ainda aberto");
        CicloVotacao antigo = novoCiclo("Mais antigo");
        antigo.setStatus(StatusCicloVotacao.ENCERRADO);
        antigo.setEncerradoEm(Instant.parse("2026-11-01T10:00:00Z"));
        ciclos.saveAndFlush(antigo);

        CicloVotacao recente = novoCiclo("Mais recente");
        recente.setStatus(StatusCicloVotacao.ENCERRADO);
        recente.setEncerradoEm(Instant.parse("2026-11-02T10:00:00Z"));
        ciclos.saveAndFlush(recente);

        mvc.perform(get("/api/ciclos-votacao/historico")
                .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.temProxima").value(true))
                .andExpect(jsonPath("$.ciclos.length()").value(1))
                .andExpect(jsonPath("$.ciclos[0].nome").value("Mais recente"))
                .andExpect(jsonPath("$.ciclos[0].totalVotos").value(0))
                .andExpect(jsonPath("$.ciclos[0].vencedorTemaId").doesNotExist());

        mvc.perform(get("/api/ciclos-votacao/historico")
                .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temProxima").value(false))
                .andExpect(jsonPath("$.ciclos[0].nome").value("Mais antigo"));
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void rejeitaPaginacaoInvalida() throws Exception {
        mvc.perform(get("/api/ciclos-votacao/historico").param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ciclos-votacao/historico").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@geniusquest.test", roles = "ADMIN")
    void fotografaRankingEVencedorMesmoDepoisDeAlterarTema() throws Exception {
        CicloVotacao ciclo = novoCiclo("Votação de Novembro");
        mvc.perform(post("/api/temas/{id}/votos", tema.getId())
                .with(user(jogador.getEmail()).roles("USUARIO")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/temas/{id}/votos", tema.getId())
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/ciclos-votacao/{id}/encerrar", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.vencedorTemaId").value(tema.getId().toString()));

        tema.setTitulo("Título editado posteriormente");
        tema.setTituloNormalizado("título editado posteriormente");
        tema.setStatus(StatusTema.REMOVIDO);
        temas.saveAndFlush(tema);

        mvc.perform(get("/api/ciclos-votacao/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.ciclos[0].nome").value("Votação de Novembro"))
                .andExpect(jsonPath("$.ciclos[0].vencedorTitulo").value("História do Futebol"))
                .andExpect(jsonPath("$.ciclos[0].totalVotos").value(2))
                .andExpect(jsonPath("$.ciclos[0].rankingReconstruido").value(false));

        mvc.perform(get("/api/ciclos-votacao/{id}/ranking", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("História do Futebol"))
                .andExpect(jsonPath("$[0].votos").value(2))
                .andExpect(jsonPath("$[0].votantesDistintos").value(2));
    }

    @Test
    @WithMockUser(username = "jogador@geniusquest.test", roles = "USUARIO")
    void rankingReconstruidoPermaneceIdentificado() throws Exception {
        CicloVotacao ciclo = novoCiclo("Histórico legado");
        ciclo.setStatus(StatusCicloVotacao.ENCERRADO);
        ciclo.setRankingReconstruido(true);
        ciclo.setEncerradoEm(Instant.parse("2026-11-01T10:00:00Z"));
        ciclos.saveAndFlush(ciclo);
        resultados.saveAndFlush(new ResultadoTemaCiclo(
                ciclo.getId(), tema.getId(), 1, "História do Futebol",
                1, 1, true));

        mvc.perform(get("/api/ciclos-votacao/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciclos[0].rankingReconstruido").value(true));
        mvc.perform(get("/api/ciclos-votacao/{id}/ranking", ciclo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].votos").value(1));
    }
}
