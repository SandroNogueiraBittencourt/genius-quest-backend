package br.com.geniusquest.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PartidaPerguntaRepositoryTest {
    @Autowired PartidaPerguntaRepository perguntas;
    @Autowired JdbcTemplate jdbc;

    private final UUID jogador = UUID.randomUUID();
    private final UUID partida = UUID.randomUUID();
    private final UUID tema = UUID.randomUUID();

    @BeforeEach
    void prepararPartida() {
        UUID categoria = UUID.randomUUID();
        registrarUsuario(jogador);
        jdbc.update("INSERT INTO categorias(id, nome, status) VALUES (?, ?, 'APROVADA')",
                categoria, "Categoria " + categoria);
        jdbc.update("""
                INSERT INTO temas(id, categoria_id, autor_id, titulo, titulo_normalizado, status)
                VALUES (?, ?, ?, 'Tema', 'tema', 'APROVADO')
                """, tema, categoria, jogador);
        jdbc.update("""
                INSERT INTO partidas(id, tema_id, criador_id, tema_titulo, categoria_nome)
                VALUES (?, ?, ?, 'Tema', 'Categoria')
                """, partida, tema, jogador);
    }

    @Test
    void quantidadeInsuficienteNaoGravaPerguntasParciais() {
        cadastrarPergunta("Completa", "APROVADA", 4);
        cadastrarPergunta("Incompleta", "APROVADA", 3);
        cadastrarPergunta("Pendente", "PENDENTE", 4);

        assertEquals(0, perguntas.copiarPerguntasAprovadas(partida, tema, 2));
        assertTrue(perguntas.buscar(partida, 1).isEmpty());
        assertEquals(1, perguntas.copiarPerguntasAprovadas(partida, tema, 1));
        assertEquals("Completa", perguntas.buscar(partida, 1).orElseThrow().enunciado());
    }

    @Test
    void preservaAlternativasNaOrdemDasPosicoes() {
        cadastrarPergunta("Completa", "APROVADA", 4);
        assertEquals(1, perguntas.copiarPerguntasAprovadas(partida, tema, 1));

        var pergunta = perguntas.buscar(partida, 1).orElseThrow();
        assertEquals(1, pergunta.totalPerguntas());
        assertEquals(List.of("Opção 0", "Opção 1", "Opção 2", "Opção 3"), pergunta.alternativas());
    }

    @Test
    void recusaRespostaDuplicadaEPreservaProgressoDosOutrosJogadores() {
        UUID convidado = UUID.randomUUID();
        registrarUsuario(convidado);
        cadastrarPergunta("Completa", "APROVADA", 4);
        assertEquals(1, perguntas.copiarPerguntasAprovadas(partida, tema, 1));

        assertFalse(perguntas.registrarResposta(partida, 2, jogador, 0));
        assertTrue(perguntas.registrarResposta(partida, 1, jogador, 0));
        assertFalse(perguntas.registrarResposta(partida, 1, jogador, 1));
        assertTrue(perguntas.proximaPergunta(partida, jogador).isEmpty());
        assertEquals(1, perguntas.proximaPergunta(partida, convidado).orElseThrow().numero());
        assertEquals(0, jdbc.queryForObject("""
                SELECT alternativa_indice FROM partida_respostas
                WHERE partida_id = ? AND numero_pergunta = 1 AND usuario_id = ?
                """, Integer.class, partida, jogador));
    }

    private void registrarUsuario(UUID id) {
        jdbc.update("INSERT INTO usuarios(id, nome, email, senha) VALUES (?, ?, ?, ?)",
                id, "Pessoa Teste", id + "@example.test", "hash");
    }

    private void cadastrarPergunta(String enunciado, String status, int alternativas) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO perguntas(id, tema_id, enunciado, enunciado_normalizado, resposta_correta_indice,
                    status, origem, dificuldade_inicial)
                VALUES (?, ?, ?, ?, 0, ?, 'MANUAL', 'FACIL')
                """, id, tema, enunciado, enunciado, status);
        for (int posicao = alternativas - 1; posicao >= 0; posicao--) {
            jdbc.update("INSERT INTO alternativas_pergunta(id, pergunta_id, posicao, texto) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), id, posicao, "Opção " + posicao);
        }
    }
}
