package br.com.geniusquest.service;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.service.ia.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        tema = new Tema();
        tema.setCategoria(categoria);
        tema.setAutor(usuario);
        tema.setTitulo("Planetas " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);
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
