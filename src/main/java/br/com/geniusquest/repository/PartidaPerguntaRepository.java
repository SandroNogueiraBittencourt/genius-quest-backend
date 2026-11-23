package br.com.geniusquest.repository;

import br.com.geniusquest.dto.partida.PerguntaPartidaDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartidaPerguntaRepository {
    private final JdbcTemplate jdbc;

    public PartidaPerguntaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int copiarPerguntasAprovadas(UUID partidaId, UUID temaId, int quantidade) {
        return jdbc.update("""
                WITH selecionadas AS (
                    SELECT p.id, p.criado_em, p.enunciado, opcoes.textos,
                        p.resposta_correta_indice, p.dificuldade_inicial
                    FROM perguntas p
                    JOIN temas t ON t.id = p.tema_id
                    JOIN categorias c ON c.id = t.categoria_id
                    JOIN LATERAL (
                        SELECT array_agg(a.texto ORDER BY a.posicao) AS textos
                        FROM alternativas_pergunta a
                        WHERE a.pergunta_id = p.id
                        HAVING COUNT(*) = 4 AND bool_and(btrim(a.texto) <> '')
                    ) opcoes ON TRUE
                    WHERE p.tema_id = ? AND p.status = 'APROVADA'
                        AND t.status = 'APROVADO' AND c.status = 'APROVADA' AND btrim(p.enunciado) <> ''
                    ORDER BY p.criado_em, p.id
                    LIMIT ?
                )
                INSERT INTO partida_perguntas (partida_id, numero, pergunta_id, enunciado,
                    alternativas, resposta_correta_indice, dificuldade_inicial)
                SELECT ?, ROW_NUMBER() OVER (ORDER BY criado_em, id), id, enunciado,
                    textos, resposta_correta_indice, dificuldade_inicial
                FROM selecionadas
                WHERE (SELECT COUNT(*) FROM selecionadas) = ?
                """, temaId, quantidade, partidaId, quantidade);
    }

    public Optional<PerguntaPartidaDTO> buscar(UUID partidaId, int numero) {
        return jdbc.query("""
                SELECT p.enunciado, p.alternativas,
                    (SELECT count(*) FROM partida_perguntas WHERE partida_id = p.partida_id) AS total
                FROM partida_perguntas p
                WHERE p.partida_id = ? AND p.numero = ?
                """, (rs, row) -> new PerguntaPartidaDTO(partidaId, numero, rs.getInt("total"),
                rs.getString("enunciado"), List.of((String[]) rs.getArray("alternativas").getArray())),
                partidaId, numero).stream().findFirst();
    }

    public boolean registrarResposta(UUID partidaId, int numero, UUID usuarioId, int alternativaIndice) {
        return jdbc.update("""
                INSERT INTO partida_respostas
                    (partida_id, numero_pergunta, usuario_id, alternativa_indice, acertou)
                SELECT partida_id, numero, ?, ?, resposta_correta_indice = ?
                FROM partida_perguntas
                WHERE partida_id = ? AND numero = ?
                ON CONFLICT (partida_id, numero_pergunta, usuario_id) DO NOTHING
                """, usuarioId, alternativaIndice, alternativaIndice, partidaId, numero) == 1;
    }

    public boolean todosResponderam(UUID codigo, UUID partidaId, int numero) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM sala_participantes participante
                    WHERE participante.codigo_sala = ? AND NOT EXISTS (
                        SELECT 1 FROM partida_respostas resposta
                        WHERE resposta.partida_id = ? AND resposta.numero_pergunta = ?
                            AND resposta.usuario_id = participante.usuario_id
                    )
                )
                """, Boolean.class, codigo, partidaId, numero));
    }
}
