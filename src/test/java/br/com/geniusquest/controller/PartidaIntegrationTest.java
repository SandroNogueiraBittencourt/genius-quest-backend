package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartidaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired ResultadoTemaCicloRepository resultados;
    @Autowired PartidaRepository partidas;

    private Usuario primeiro;
    private Usuario segundo;
    private Tema tema;
    private CicloVotacao ciclo;

    @BeforeEach
    void preparar() {
        primeiro = usuario("criador");
        segundo = usuario("outro");
        Categoria categoria = new Categoria();
        categoria.setNome("Animais " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        tema = new Tema();
        tema.setTitulo("Animais Selvagens");
        tema.setTituloNormalizado("animais selvagens");
        tema.setCategoria(categoria);
        tema.setAutor(primeiro);
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);

        ciclo = ciclo("Votação Animais", StatusCicloVotacao.ENCERRADO, true);
        resultados.saveAndFlush(new ResultadoTemaCiclo(
                ciclo.getId(), tema.getId(), 1, "Animais Selvagens", 3, 3, false));
    }

    private Usuario usuario(String prefixo) {
        Usuario usuario = new Usuario();
        usuario.setNome(prefixo);
        usuario.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha("hash-para-teste");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(usuario);
    }

    private CicloVotacao ciclo(String nome, StatusCicloVotacao status, boolean comVencedor) {
        CicloVotacao novo = new CicloVotacao();
        novo.setNome(nome + " " + UUID.randomUUID());
        novo.setInicioEm(Instant.now().minusSeconds(7200));
        novo.setFimEm(Instant.now().minusSeconds(3600));
        novo.setStatus(status);
        if (status == StatusCicloVotacao.ENCERRADO) novo.setEncerradoEm(Instant.now());
        if (comVencedor) novo.setVencedor(tema);
        return ciclos.saveAndFlush(novo);
    }

    private String pedido(UUID id) {
        return "{\"cicloId\":\"" + id + "\"}";
    }

    @Test
    void endpointsExigemAutenticacao() throws Exception {
        mvc.perform(post("/api/partidas").contentType(MediaType.APPLICATION_JSON)
                .content(pedido(ciclo.getId()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/minhas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criaPartidaComTemaDefinidoPeloServidorEStatusPreparacao() throws Exception {
        mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/partidas/")))
                .andExpect(jsonPath("$.cicloId").value(ciclo.getId().toString()))
                .andExpect(jsonPath("$.temaId").value(tema.getId().toString()))
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"))
                .andExpect(jsonPath("$.status").value("PREPARACAO"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void bloqueiaCicloInexistenteAbertoOuSemVencedor() throws Exception {
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        CicloVotacao aberto = ciclo("Aberto", StatusCicloVotacao.ABERTO, false);
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(aberto.getId())))
                .andExpect(status().isConflict());

        CicloVotacao semVencedor = ciclo("Sem vencedor", StatusCicloVotacao.ENCERRADO, false);
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(semVencedor.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void validaPayloadDePartida() throws Exception {
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protegeConsultasDePartidaPorCriador() throws Exception {
        var resultado = mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(resultado.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/partidas/{id}", id)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"));

        mvc.perform(get("/api/partidas/{id}", id)
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/partidas/minhas")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(get("/api/partidas/minhas")
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void preservaTituloHistoricoMesmoAposAlteracaoDoTema() throws Exception {
        tema.setTitulo("Título alterado");
        temas.saveAndFlush(tema);

        mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"));
    }
}
