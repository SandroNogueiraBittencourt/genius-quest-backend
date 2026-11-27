package br.com.geniusquest.repository;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PerguntaRepositoryTest {
    @Autowired PerguntaRepository perguntas;
    @Autowired TemaRepository temas;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    private Tema criarTema() {
        Usuario autor = new Usuario();
        autor.setNome("Teste IA");
        autor.setEmail("teste-" + UUID.randomUUID() + "@geniusquest.test");
        autor.setSenha("hash-para-teste");
        autor = usuarios.saveAndFlush(autor);

        Categoria categoria = new Categoria();
        categoria.setNome("Ciências " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        Tema tema = new Tema();
        tema.setCategoria(categoria);
        tema.setAutor(autor);
        tema.setTitulo("Planetas " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setStatus(StatusTema.APROVADO);
        return temas.saveAndFlush(tema);
    }

    @Test
    void persistePerguntaComQuatroAlternativasEGabaritoPrivado() {
        Tema tema = criarTema();
        String enunciado = "Qual planeta é conhecido como planeta vermelho? " + UUID.randomUUID();
        var validado = new ValidadorPergunta().validar(enunciado,
                List.of("Marte", "Vênus", "Júpiter", "Saturno"), 0);

        Pergunta candidata = new Pergunta();
        candidata.setTema(tema);
        candidata.setEnunciado(validado.enunciado());
        candidata.setEnunciadoNormalizado(validado.enunciadoNormalizado());
        candidata.setRespostaCorretaIndice(validado.respostaCorretaIndice());
        candidata.setOrigem(OrigemPergunta.MANUAL);
        candidata.setDificuldadeInicial(DificuldadePergunta.MEDIA);
        for (int i = 0; i < validado.alternativas().size(); i++) {
            candidata.adicionarAlternativa(i, validado.alternativas().get(i));
        }
        Pergunta salva = perguntas.saveAndFlush(candidata);
        assertNotNull(salva.getId());
        assertEquals(StatusPergunta.PENDENTE, salva.getStatus());
        assertEquals(4, salva.getAlternativas().size());
        assertEquals(0, salva.getRespostaCorretaIndice());
        assertTrue(perguntas.existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
                tema.getId(), validado.enunciadoNormalizado(),
                List.of(StatusPergunta.PENDENTE, StatusPergunta.APROVADA)));
        assertEquals(1, perguntas.findByTema_IdAndStatusOrderByCriadoEmAsc(
                tema.getId(), StatusPergunta.PENDENTE).size());
    }

    @Test
    void limitaPerguntasElegiveisADezEFiltraPorTemaEStatus() {
        Tema tema = criarTema();
        List<UUID> esperados = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            Pergunta pergunta = registrarPergunta(tema, "Aprovada " + i, StatusPergunta.APROVADA, i);
            if (i <= 10) esperados.add(pergunta.getId());
        }
        registrarPergunta(tema, "Pendente", StatusPergunta.PENDENTE, 0);
        registrarPergunta(criarTema(), "Outro tema", StatusPergunta.APROVADA, 0);

        var encontradas = perguntas.findTop10ByTema_IdAndStatusOrderByCriadoEmAscIdAsc(
                tema.getId(), StatusPergunta.APROVADA);

        assertEquals(esperados, encontradas.stream().map(Pergunta::getId).toList());
    }

    @Test
    void buscaEnunciadosRecentesComFiltroELimiteNoBanco() {
        Tema tema = criarTema();
        registrarPergunta(tema, "Aprovada antiga", StatusPergunta.APROVADA, 1);
        registrarPergunta(tema, "Pendente recente", StatusPergunta.PENDENTE, 2);
        registrarPergunta(tema, "Aprovada recente", StatusPergunta.APROVADA, 3);
        registrarPergunta(tema, "Rejeitada", StatusPergunta.REJEITADA, 4);
        registrarPergunta(criarTema(), "Outro tema", StatusPergunta.APROVADA, 5);

        var recentes = perguntas.buscarEnunciadosRecentes(tema.getId(),
                List.of(StatusPergunta.APROVADA, StatusPergunta.PENDENTE),
                PageRequest.of(0, 2));

        assertEquals(List.of("Aprovada recente", "Pendente recente"), recentes);
    }

    private Pergunta registrarPergunta(Tema tema, String enunciado, StatusPergunta status, int segundos) {
        Pergunta pergunta = new Pergunta();
        pergunta.setTema(tema);
        pergunta.setEnunciado(enunciado);
        pergunta.setEnunciadoNormalizado(enunciado.toLowerCase(java.util.Locale.ROOT));
        pergunta.setRespostaCorretaIndice(0);
        pergunta.setOrigem(OrigemPergunta.MANUAL);
        pergunta.setDificuldadeInicial(DificuldadePergunta.MEDIA);
        pergunta.setStatus(status);
        pergunta = perguntas.saveAndFlush(pergunta);
        jdbc.update("UPDATE perguntas SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-10-01T12:00:00Z").plusSeconds(segundos)), pergunta.getId());
        return pergunta;
    }
}
