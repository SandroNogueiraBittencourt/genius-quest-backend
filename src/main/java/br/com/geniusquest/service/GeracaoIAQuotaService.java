package br.com.geniusquest.service;

import br.com.geniusquest.exception.RegraTemasException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cota global PostgreSQL (incluindo reinicios e multiplas instancias).
 * A linha do dia e bloqueada na mesma transacao da reserva da chave UUID.
 * Tentativas fracassadas continuam contando: timeout pode ter consumido tokens.
 */
@Service
public class GeracaoIAQuotaService {
    public static final int MAX_POR_SOLICITACAO = 3;
    public static final int MAX_REQUISICOES_DIA = 4;
    public static final int MAX_PERGUNTAS_DIA = 10;

    private static final String HOJE_UTC = "(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date";

    private final JdbcTemplate jdbc;

    public record Uso(int requisicoes, int perguntas) {
    }

    public GeracaoIAQuotaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Uso usoHoje() {
        List<Uso> hoje = jdbc.query(
                "SELECT requisicoes, perguntas FROM ia_geracao_cota_diaria WHERE dia_utc = "
                        + HOJE_UTC,
                (rs, n) -> new Uso(rs.getInt(1), rs.getInt(2)));
        return hoje.isEmpty() ? new Uso(0, 0) : hoje.get(0);
    }

    @Transactional
    public void reservar(UUID chave, String solicitante, UUID temaId, int quantidade) {
        if (chave == null || temaId == null || solicitante == null || solicitante.isBlank()
                || quantidade < 1 || quantidade > MAX_POR_SOLICITACAO) {
            throw new RegraTemasException(HttpStatus.BAD_REQUEST,
                    "Solicitacao IA invalida.");
        }
        jdbc.update("INSERT INTO ia_geracao_cota_diaria (dia_utc) VALUES ("
                + HOJE_UTC + ") ON CONFLICT DO NOTHING");
        Uso uso = jdbc.queryForObject(
                "SELECT requisicoes, perguntas FROM ia_geracao_cota_diaria WHERE dia_utc = "
                        + HOJE_UTC + " FOR UPDATE",
                (rs, n) -> new Uso(rs.getInt(1), rs.getInt(2)));
        Integer existente = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ia_geracao_solicitacoes WHERE chave_idempotencia = ?",
                Integer.class, chave);
        if (existente != null && existente > 0) {
            throw new RegraTemasException(HttpStatus.CONFLICT,
                    "Essa solicitacao ja foi registrada. Verifique a fila antes de solicitar outra.");
        }
        if (uso == null || uso.requisicoes() >= MAX_REQUISICOES_DIA
                || uso.perguntas() + quantidade > MAX_PERGUNTAS_DIA) {
            throw new RegraTemasException(HttpStatus.TOO_MANY_REQUESTS,
                    "Cota diaria de geracao IA esgotada.");
        }
        int inseridos = jdbc.update(
                "INSERT INTO ia_geracao_solicitacoes "
                        + "(chave_idempotencia, solicitante, tema_id, quantidade, status) "
                        + "VALUES (?, ?, ?, ?, 'RESERVADA') ON CONFLICT DO NOTHING",
                chave, solicitante, temaId, quantidade);
        if (inseridos != 1) {
            throw new RegraTemasException(HttpStatus.CONFLICT,
                    "Essa solicitacao ja foi registrada. Verifique a fila antes de solicitar outra.");
        }
        jdbc.update("UPDATE ia_geracao_cota_diaria "
                + "SET requisicoes = requisicoes + 1, perguntas = perguntas + ? "
                + "WHERE dia_utc = " + HOJE_UTC, quantidade);
    }

    @Transactional
    public void concluir(UUID chave) {
        marcar(chave, "CONCLUIDA");
    }

    @Transactional
    public void falhar(UUID chave) {
        marcar(chave, "FALHOU");
    }

    private void marcar(UUID chave, String estado) {
        jdbc.update("UPDATE ia_geracao_solicitacoes SET status = ?, "
                + "concluida_em = CURRENT_TIMESTAMP "
                + "WHERE chave_idempotencia = ? AND status = 'RESERVADA'",
                estado, chave);
    }
}
