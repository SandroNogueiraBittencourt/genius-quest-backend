package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartidaRespostasIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired PartidaRepository partidas;
    @Autowired PerguntaRepository perguntas;
    @Autowired PerguntaPartidaRepository snapshots;
    @Autowired RespostaPartidaRepository respostas;

    Usuario dono;
    Usuario outro;
    Tema tema;
    Partida partida;

    @BeforeEach
    void preparar() {
        dono = usuario("dono");
        outro = usuario("outro");
        Categoria categoria = new Categoria();
        categoria.setNome("Categoria " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        tema = new Tema();
        tema.setTitulo("Animais pré-históricos " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setCategoria(categoria);
        tema.setAutor(dono);
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);

        CicloVotacao ciclo = new CicloVotacao();
        ciclo.setNome("Votação " + UUID.randomUUID());
        ciclo.setInicioEm(Instant.now().minusSeconds(7200));
        ciclo.setFimEm(Instant.now().minusSeconds(3600));
        ciclo.setStatus(StatusCicloVotacao.ENCERRADO);
        ciclo.setEncerradoEm(Instant.now());
        ciclo.setVencedor(tema);
        ciclo = ciclos.saveAndFlush(ciclo);

        partida = new Partida();
        partida.setCriador(dono);
        partida.setCiclo(ciclo);
        partida.setTema(tema);
        partida.setTemaTitulo(tema.getTitulo());
        partida.setCategoriaNome(categoria.getNome());
        partida = partidas.saveAndFlush(partida);
    }

    private Usuario usuario(String prefixo) {
        Usuario u = new Usuario();
        u.setNome(prefixo);
        u.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        u.setSenha("hash-de-teste");
        u.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(u);
    }

    private Pergunta pergunta(StatusPergunta status) {
        var validado = new ValidadorPergunta().validar(
                "Qual destes animais era um réptil marinho? " + UUID.randomUUID(),
                List.of("Mosassauro", "Tiranossauro", "Tricerátops", "Estegossauro"), 0);
        Pergunta p = new Pergunta();
        p.setTema(tema);
        p.setEnunciado(validado.enunciado());
        p.setEnunciadoNormalizado(validado.enunciadoNormalizado());
        p.setRespostaCorretaIndice(validado.respostaCorretaIndice());
        p.setStatus(status);
        p.setOrigem(OrigemPergunta.IA);
        p.setDificuldadeInicial(DificuldadePergunta.MEDIA);
        p.setFonteReferencia("https://www.nhm.ac.uk/discover/what-are-dinosaurs.html");
        for (int i = 0; i < 4; i++) p.adicionarAlternativa(i, validado.alternativas().get(i));
        return perguntas.saveAndFlush(p);
    }

    private String resposta(Pergunta pergunta, int indice) {
        return "{\"perguntaId\":\"" + pergunta.getId()
                + "\",\"alternativaIndice\":" + indice + "}";
    }

    private void iniciar() throws Exception {
        mvc.perform(post("/api/partidas/{id}/iniciar", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    void exigeJwtEOcultaPartidasDeOutrasContas() throws Exception {
        Pergunta p = pergunta(StatusPergunta.APROVADA);

        mvc.perform(post("/api/partidas/{id}/iniciar", partida.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/partidas/{id}/iniciar", partida.getId())
                .with(user(outro.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/partidas/{id}/iniciar", UUID.randomUUID())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        iniciar();

        mvc.perform(get("/api/partidas/{id}/respostas", partida.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/{id}/respostas", partida.getId())
                .with(user(outro.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .contentType(MediaType.APPLICATION_JSON).content(resposta(p, 0)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(outro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(p, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void partidaVinculadaASalaUsaOFluxoDoMultiplayer() throws Exception {
        Pergunta p = pergunta(StatusPergunta.APROVADA);
        var criador = user(dono.getEmail()).roles("USUARIO");
        UUID id = partida.getId();

        mvc.perform(post("/api/partidas/{id}/sala", id).with(criador))
                .andExpect(status().isOk());
        mvc.perform(post("/api/partidas/{id}/iniciar", id).with(criador))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/partidas/{id}/perguntas", id).with(criador))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/partidas/{id}/respostas", id).with(criador))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/partidas/{id}/respostas", id).with(criador)
                .contentType(MediaType.APPLICATION_JSON).content(resposta(p, 0)))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(snapshots.findByPartida_IdOrderByOrdemAsc(id).isEmpty());
    }

    @Test
    void partidaIndividualEmAndamentoRecusaVinculoComSala() throws Exception {
        pergunta(StatusPergunta.APROVADA);
        iniciar();

        mvc.perform(post("/api/partidas/{id}/sala", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void impedeIniciarSemPerguntasAprovadasOuIniciarNovamente() throws Exception {
        pergunta(StatusPergunta.PENDENTE);
        mvc.perform(post("/api/partidas/{id}/iniciar", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(0, snapshots.count());
        org.junit.jupiter.api.Assertions.assertEquals(StatusPartida.PREPARACAO,
                partidas.findById(partida.getId()).orElseThrow().getStatus());

        pergunta(StatusPergunta.APROVADA);
        iniciar();
        mvc.perform(post("/api/partidas/{id}/iniciar", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, snapshots.count());
    }

    @Test
    void congelaConteudoEGabaritoEPermiteUmaSoResposta() throws Exception {
        Pergunta p = pergunta(StatusPergunta.APROVADA);
        String original = p.getEnunciado();
        iniciar();
        org.junit.jupiter.api.Assertions.assertEquals(1, snapshots.count());

        // Mesmo com a questão original posteriormente alterada ou bloqueada,
        // o jogador vê somente o snapshot histórico, e a correção usa o gabarito inicial.
        p.setEnunciado("Enunciado alterado na curadoria");
        p.getAlternativas().get(0).setTexto("Alternativa alterada");
        p.setRespostaCorretaIndice(1);
        p.setStatus(StatusPergunta.BLOQUEADA);
        perguntas.saveAndFlush(p);

        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(p.getId().toString()))
                .andExpect(jsonPath("$[0].enunciado").value(original))
                .andExpect(jsonPath("$[0].alternativas[0]").value("Mosassauro"))
                .andExpect(jsonPath("$[0].respostaCorretaIndice").doesNotExist())
                .andExpect(jsonPath("$[0].fonteReferencia").doesNotExist())
                .andExpect(jsonPath("$[0].correta").doesNotExist());

        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(p, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perguntaId").value(p.getId().toString()))
                .andExpect(jsonPath("$.alternativaIndice").value(0))
                .andExpect(jsonPath("$.correta").doesNotExist())
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist());

        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(p, 1)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correta").doesNotExist())
                .andExpect(jsonPath("$[0].respostaCorretaIndice").doesNotExist());
        org.junit.jupiter.api.Assertions.assertEquals(1, respostas.count());
        org.junit.jupiter.api.Assertions.assertTrue(respostas.findAll().get(0).isCorreta());
        org.junit.jupiter.api.Assertions.assertEquals(StatusPartida.EM_ANDAMENTO,
                partidas.findById(partida.getId()).orElseThrow().getStatus());
    }

    @Test
    void recusaPayloadInvalidoPerguntaForaDaPartidaERespostaAntesDeIniciar() throws Exception {
        Pergunta correta = pergunta(StatusPergunta.APROVADA);
        Pergunta pendente = pergunta(StatusPergunta.PENDENTE);
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(correta, 0)))
                .andExpect(status().isConflict());

        iniciar();
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(correta, 4)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(pendente, 0)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/partidas/{id}/respostas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(correta, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correta").doesNotExist());
        org.junit.jupiter.api.Assertions.assertFalse(respostas.findAll().get(0).isCorreta());
    }

    @Test
    void congelaNoMaximoDezPerguntas() throws Exception {
        for (int i = 0; i < 11; i++) pergunta(StatusPergunta.APROVADA);
        iniciar();
        org.junit.jupiter.api.Assertions.assertEquals(10, snapshots.count());
        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }
}
