package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartidaPerguntasElegiveisIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired PartidaRepository partidas;
    @Autowired PerguntaRepository perguntas;

    Usuario dono;
    Usuario outro;
    Tema vencedor;
    Partida partida;

    @BeforeEach
    void preparar() {
        dono = usuario("dono");
        outro = usuario("outro");
        Categoria categoria = new Categoria();
        categoria.setNome("Categoria " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        vencedor = tema(categoria, "Animais pré-históricos");
        CicloVotacao ciclo = new CicloVotacao();
        ciclo.setNome("Votação " + UUID.randomUUID());
        ciclo.setInicioEm(java.time.Instant.now().minusSeconds(7200));
        ciclo.setFimEm(java.time.Instant.now().minusSeconds(3600));
        ciclo.setStatus(StatusCicloVotacao.ENCERRADO);
        ciclo.setEncerradoEm(java.time.Instant.now());
        ciclo.setVencedor(vencedor);
        ciclo = ciclos.saveAndFlush(ciclo);

        partida = new Partida();
        partida.setCriador(dono);
        partida.setCiclo(ciclo);
        partida.setTema(vencedor);
        partida.setTemaTitulo(vencedor.getTitulo());
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

    private Tema tema(Categoria categoria, String titulo) {
        Tema t = new Tema();
        t.setCategoria(categoria);
        t.setAutor(dono);
        t.setTitulo(titulo + " " + UUID.randomUUID());
        t.setTituloNormalizado(t.getTitulo().toLowerCase(java.util.Locale.ROOT));
        t.setStatus(StatusTema.APROVADO);
        return temas.saveAndFlush(t);
    }

    private Pergunta pergunta(Tema tema, StatusPergunta status) {
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
        for (int i = 0; i < 4; i++) p.adicionarAlternativa(i, validado.alternativas().get(i));
        return perguntas.saveAndFlush(p);
    }

    @Test
    void exigeAutenticacaoEOcultaPartidaDeOutraConta() throws Exception {
        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(outro.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/partidas/{id}/perguntas", UUID.randomUUID())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void soEntregaAprovadasDoTemaVencedorSemGabaritoNemAuditoria() throws Exception {
        Pergunta aprovada = pergunta(vencedor, StatusPergunta.APROVADA);
        pergunta(vencedor, StatusPergunta.PENDENTE);
        pergunta(vencedor, StatusPergunta.REJEITADA);
        pergunta(vencedor, StatusPergunta.BLOQUEADA);
        Tema outroTema = tema(vencedor.getCategoria(), "Outro tema");
        pergunta(outroTema, StatusPergunta.APROVADA);

        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aprovada.getId().toString()))
                .andExpect(jsonPath("$[0].enunciado").isString())
                .andExpect(jsonPath("$[0].alternativas.length()").value(4))
                .andExpect(jsonPath("$[0].dificuldadeInicial").value("MEDIA"))
                .andExpect(jsonPath("$[0].respostaCorretaIndice").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].origem").doesNotExist())
                .andExpect(jsonPath("$[0].fonteReferencia").doesNotExist())
                .andExpect(jsonPath("$[0].modeloIa").doesNotExist())
                .andExpect(jsonPath("$[0].revisorId").doesNotExist())
                .andExpect(jsonPath("$[0].motivoRevisao").doesNotExist());

        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(outro.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaVaziaSemAprovadasELimiteMaximoDeDez() throws Exception {
        pergunta(vencedor, StatusPergunta.PENDENTE);
        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        for (int i = 0; i < 11; i++) pergunta(vencedor, StatusPergunta.APROVADA);
        mvc.perform(get("/api/partidas/{id}/perguntas", partida.getId())
                .with(user(dono.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }
}
