package br.com.geniusquest.repository;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void persistePerguntaComQuatroAlternativasEGabaritoPrivado() {
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
        tema = temas.saveAndFlush(tema);

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
}
