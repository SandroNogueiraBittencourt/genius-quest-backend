package br.com.geniusquest.repository;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A revogacao e compartilhada entre todas as instancias atraves do PostgreSQL.
 * Nunca armazena o token original.
 */
@Repository
public class TokenRevogadoRepository {

    private final JdbcTemplate jdbc;

    public TokenRevogadoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existe(String hashToken) {
        Boolean encontrado = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM jwt_tokens_revogados WHERE token_hash = ?)",
                Boolean.class,
                hashToken);
        return Boolean.TRUE.equals(encontrado);
    }

    public void registrar(String hashToken, Instant expiraEm) {
        jdbc.update(
                "INSERT INTO jwt_tokens_revogados (token_hash, expira_em) "
                        + "VALUES (?, ?) ON CONFLICT (token_hash) DO NOTHING",
                hashToken,
                Timestamp.from(expiraEm));
    }

    public int removerExpirados(Instant agora) {
        return jdbc.update(
                "DELETE FROM jwt_tokens_revogados WHERE expira_em <= ?",
                Timestamp.from(agora));
    }
}
