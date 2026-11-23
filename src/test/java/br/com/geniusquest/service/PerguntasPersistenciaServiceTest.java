package br.com.geniusquest.service;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.service.ia.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PerguntasPersistenciaServiceTest {
    @Autowired PerguntasPersistenciaService persistencia;
    @Autowired PerguntaRepository perguntas;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    private Tema tema;

    @BeforeEach
    void preparar() {
        Usuario usuario = new Usuario();
        usuario.setNome("Curadoria IA");
        usuario.setEmail("curadoria-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha("hash-para-teste");
        usuario = usuarios.saveAndFlush(usuario);
        Categoria categoria = new Categoria();
        categoria.setNome("Astronomia " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);
        tema = criarTema("Planetas", categoria, usuario);
    }

    private Tema criarTema(String titulo, Categoria categoria, Usuario usuario) {
        Tema tema = new Tema();
        tema.setCategoria(categoria);
        tema.setAutor(usuario);
        tema.setTitulo(titulo + " " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setStatus(StatusTema.APROVADO);
        return temas.saveAndFlush(tema);
    }

    private CandidataGeradaIA candidata(String enunciado) {
        return new CandidataGeradaIA(enunciado,
                List.of("Marte", "Vênus", "Júpiter", "Saturno"), 0,
                DificuldadePergunta.FACIL, null);
    }

    @Test
    void gravaCandidatasComoPendentesComProveniencia() {
        var contexto = persistencia.contexto(tema.getId());
        assertEquals(tema.getTitulo(), contexto.tituloTema());
        assertEquals(List.of(), contexto.enunciadosExistentes());
        var lote = new LoteGeradoIA("modelo-teste", List.of(candidata(
                "Qual planeta é conhecido como planeta vermelho?")));
        var ids = persistencia.salvarCandidatas(tema.getId(), lote, 1);
        assertEquals(1, ids.size());
        var salva = perguntas.findById(ids.get(0)).orElseThrow();
        assertEquals(StatusPergunta.PENDENTE, salva.getStatus());
        assertEquals(OrigemPergunta.IA, salva.getOrigem());
        assertEquals("modelo-teste", salva.getModeloIa());
        assertEquals(4, salva.getAlternativas().size());
        assertEquals(0, salva.getRespostaCorretaIndice());
    }

    @Test
    void contextoIncluiSomenteEnunciadosAtivosDoTemaEValidacaoRecusaRepeticao() {
        var aprovada = candidata("Qual planeta é conhecido como planeta vermelho?");
        var pendente = candidata("Qual planeta tem o dia mais longo?");
        var bloqueada = candidata("Em qual planeta está a Grande Mancha Vermelha?");
        var rejeitada = candidata("Qual planeta tem o ano mais curto?");
        var ids = persistencia.salvarCandidatas(tema.getId(),
                new LoteGeradoIA("modelo-teste", List.of(aprovada, pendente, bloqueada, rejeitada)), 4);
        perguntas.findById(ids.get(0)).orElseThrow().setStatus(StatusPergunta.APROVADA);
        perguntas.findById(ids.get(2)).orElseThrow().setStatus(StatusPergunta.BLOQUEADA);
        perguntas.findById(ids.get(3)).orElseThrow().setStatus(StatusPergunta.REJEITADA);

        Tema outroTema = criarTema("Estrelas", tema.getCategoria(), tema.getAutor());
        persistencia.salvarCandidatas(outroTema.getId(), new LoteGeradoIA("modelo-teste",
                List.of(candidata("Qual é a estrela mais próxima da Terra?"))), 1);

        var contexto = persistencia.contexto(tema.getId());
        assertEquals(Set.of(aprovada.enunciado(), pendente.enunciado(), bloqueada.enunciado()),
                Set.copyOf(contexto.enunciadosExistentes()));
        var repetida = new LoteGeradoIA("modelo-teste", List.of(aprovada));
        var erro = assertThrows(RegraTemasException.class,
                () -> persistencia.salvarCandidatas(tema.getId(), repetida, 1));
        assertEquals(HttpStatus.CONFLICT, erro.getStatus());
    }

    @Test
    void contextoLimitaAosVinteEnunciadosMaisRecentes() {
        var antiga = candidata("Qual planeta é conhecido como planeta vermelho?");
        persistencia.salvarCandidatas(tema.getId(),
                new LoteGeradoIA("modelo-teste", List.of(antiga)), 1);
        var recentes = IntStream.rangeClosed(1, 20)
                .mapToObj(numero -> candidata("Qual é a característica do planeta " + numero + "?"))
                .toList();
        persistencia.salvarCandidatas(tema.getId(),
                new LoteGeradoIA("modelo-teste", recentes.subList(0, 10)), 10);
        persistencia.salvarCandidatas(tema.getId(),
                new LoteGeradoIA("modelo-teste", recentes.subList(10, 20)), 10);

        var enunciados = persistencia.contexto(tema.getId()).enunciadosExistentes();
        assertEquals(20, enunciados.size());
        assertFalse(enunciados.contains(antiga.enunciado()));
    }

    @Test
    void rejeitaLoteComDuplicadasAntesDeSalvar() {
        var lote = new LoteGeradoIA("modelo-teste", List.of(
                candidata("Qual planeta é conhecido como planeta vermelho?"),
                candidata("Qual planeta é conhecido como planeta vermelho?")));
        assertThrows(RegraTemasException.class,
                () -> persistencia.salvarCandidatas(tema.getId(), lote, 2));
        assertEquals(0, perguntas.findByTema_IdAndStatusOrderByCriadoEmAsc(
                tema.getId(), StatusPergunta.PENDENTE).size());
    }
}
