package br.com.geniusquest.repository;

import br.com.geniusquest.dto.partida.ResultadoPartidaDTO.Jogador;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ResultadoPartidaRepository {
    private final JdbcTemplate jdbc;

    public ResultadoPartidaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(UUID partidaId) {
        jdbc.update("""
                INSERT INTO resultados_partida (partida_id, usuario_id, nome, pontos, acertos, erros)
                SELECT partida_id, usuario_id, nome, pontos, acertos, erros
                FROM pontuacao_partida_concluida
                WHERE partida_id = ?
                ON CONFLICT (partida_id, usuario_id) DO NOTHING
                """, partidaId);
    }

    public boolean constaNoResultado(UUID partidaId, UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM resultados_partida WHERE partida_id = ? AND usuario_id = ?
                )
                """, Boolean.class, partidaId, usuarioId));
    }

    public List<Jogador> classificar(UUID partidaId) {
        return jdbc.query("""
                SELECT usuario_id, nome, pontos, acertos, erros,
                    RANK() OVER (ORDER BY pontos DESC) AS posicao
                FROM resultados_partida
                WHERE partida_id = ?
                ORDER BY pontos DESC, usuario_id
                """, (rs, row) -> new Jogador(rs.getObject("usuario_id", UUID.class),
                rs.getString("nome"), rs.getInt("posicao"), rs.getInt("pontos"),
                rs.getInt("acertos"), rs.getInt("erros")), partidaId);
    }
}
