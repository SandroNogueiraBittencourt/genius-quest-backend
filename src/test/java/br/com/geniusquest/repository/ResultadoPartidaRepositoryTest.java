package br.com.geniusquest.repository;

import br.com.geniusquest.dto.partida.ResultadoPartidaDTO.Jogador;
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
class ResultadoPartidaRepositoryTest {
    @Autowired ResultadoPartidaRepository resultados;
    @Autowired JdbcTemplate jdbc;

    private UUID criador;
    private UUID tema;
    private UUID partida;

    @BeforeEach
    void prepararSala() {
        criador = usuario("Criador Teste");
        UUID categoria = UUID.randomUUID();
        tema = UUID.randomUUID();
        jdbc.update("INSERT INTO categorias(id, nome) VALUES (?, ?)", categoria, "Categoria " + categoria);
        jdbc.update("""
                INSERT INTO temas(id, categoria_id, autor_id, titulo, titulo_normalizado)
                VALUES (?, ?, ?, 'Tema Teste', 'tema teste')
                """, tema, categoria, criador);
        partida = partida();
    }

    @Test
    void classificaEmpatadosNaMesmaPosicaoEIsolaPartidas() {
        UUID segundo = usuario("Segundo Teste");
        UUID terceiro = usuario("Terceiro Teste");
        UUID intruso = usuario("Outra Partida");
        registrarPlacar(partida, criador, 20);
        registrarPlacar(partida, segundo, 20);
        registrarPlacar(partida, terceiro, 10);
        registrarPlacar(partida(), intruso, 30);

        var placar = resultados.classificar(partida);
        assertEquals(List.of(20, 20, 10), placar.stream().map(Jogador::pontos).toList());
        assertEquals(List.of(1, 1, 3), placar.stream().map(Jogador::posicao).toList());
        assertTrue(resultados.constaNoResultado(partida, criador));
        assertFalse(resultados.constaNoResultado(partida, intruso));
    }

    @Test
    void registraSomenteAposConclusaoEPreservaNomeOriginalAoRepetir() {
        UUID pergunta = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO perguntas(id, tema_id, enunciado, enunciado_normalizado, resposta_correta_indice,
                    origem, dificuldade_inicial)
                VALUES (?, ?, 'Pergunta', 'pergunta', 0, 'MANUAL', 'DIFICIL')
                """, pergunta, tema);
        jdbc.update("""
                INSERT INTO partida_perguntas(partida_id, numero, pergunta_id, enunciado, alternativas,
                    resposta_correta_indice, dificuldade_inicial)
                VALUES (?, 1, ?, 'Pergunta', ARRAY['A','B','C','D'], 0, 'DIFICIL')
                """, partida, pergunta);
        jdbc.update("""
                INSERT INTO partida_respostas(partida_id, numero_pergunta, usuario_id, alternativa_indice, acertou)
                VALUES (?, 1, ?, 0, true)
                """, partida, criador);

        resultados.registrar(partida);
        assertTrue(resultados.classificar(partida).isEmpty());
        jdbc.update("UPDATE partidas SET status = 'CONCLUIDA' WHERE id = ?", partida);
        resultados.registrar(partida);
        jdbc.update("UPDATE usuarios SET nome = 'Nome alterado' WHERE id = ?", criador);
        resultados.registrar(partida);

        assertEquals(List.of(new Jogador(criador, "Criador Teste", 1, 30, 1, 0)), resultados.classificar(partida));
    }

    private UUID usuario(String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuarios(id, nome, email, senha) VALUES (?, ?, ?, 'hash')",
                id, nome, id + "@example.test");
        return id;
    }

    private UUID partida() {
        UUID id = UUID.randomUUID();
        UUID sala = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partidas(id, tema_id, criador_id, tema_titulo, categoria_nome)
                VALUES (?, ?, ?, 'Tema Teste', 'Categoria Teste')
                """, id, tema, criador);
        jdbc.update("INSERT INTO salas(codigo, anfitriao_id, partida_id) VALUES (?, ?, ?)", sala, criador, id);
        jdbc.update("INSERT INTO sala_partidas(partida_id, codigo_sala) VALUES (?, ?)", id, sala);
        return id;
    }

    private void registrarPlacar(UUID partidaId, UUID usuario, int pontos) {
        jdbc.update("""
                INSERT INTO resultados_partida(partida_id, usuario_id, nome, pontos, acertos, erros)
                VALUES (?, ?, 'Pessoa Teste', ?, 1, 0)
                """, partidaId, usuario, pontos);
    }
}
