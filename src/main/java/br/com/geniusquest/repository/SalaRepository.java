package br.com.geniusquest.repository;

import br.com.geniusquest.dto.partida.SalaResponseDTO.Participante;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SalaRepository {
    public record Sala(UUID partidaId, UUID anfitriaoId, boolean votacaoAberta) {
        public Sala(UUID partidaId, UUID anfitriaoId) {
            this(partidaId, anfitriaoId, false);
        }
    }

    public record TotalVotos(UUID temaId, long votos) {}

    private final JdbcTemplate jdbc;

    public SalaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> buscarCodigo(UUID partidaId) {
        return jdbc.query("SELECT codigo FROM salas WHERE partida_id = ?",
                (rs, row) -> rs.getObject("codigo", UUID.class), partidaId).stream().findFirst();
    }

    public Optional<Sala> buscar(UUID codigo) {
        return buscar(codigo, "SELECT partida_id, anfitriao_id, votacao_aberta FROM salas WHERE codigo = ?");
    }

    public Optional<Sala> buscarParaAtualizacao(UUID codigo) {
        return buscar(codigo, "SELECT partida_id, anfitriao_id, votacao_aberta FROM salas WHERE codigo = ? FOR UPDATE");
    }

    private Optional<Sala> buscar(UUID codigo, String sql) {
        return jdbc.query(sql, (rs, row) -> new Sala(rs.getObject("partida_id", UUID.class),
                rs.getObject("anfitriao_id", UUID.class), rs.getBoolean("votacao_aberta")), codigo).stream().findFirst();
    }

    public void criar(UUID codigo, UUID partidaId, UUID anfitriaoId) {
        jdbc.update("INSERT INTO salas (codigo, partida_id, anfitriao_id) VALUES (?, ?, ?)",
                codigo, partidaId, anfitriaoId);
    }

    public void vincularPartida(UUID codigo, UUID partidaId) {
        jdbc.update("UPDATE salas SET partida_id = ? WHERE codigo = ?", partidaId, codigo);
    }

    public void definirVotacaoAberta(UUID codigo, boolean aberta) {
        jdbc.update("UPDATE salas SET votacao_aberta = ? WHERE codigo = ?", aberta, codigo);
    }

    public void votar(UUID codigo, UUID usuarioId, UUID temaId) {
        jdbc.update("""
                INSERT INTO sala_votos (codigo_sala, usuario_id, tema_id) VALUES (?, ?, ?)
                ON CONFLICT (codigo_sala, usuario_id) DO UPDATE SET tema_id = EXCLUDED.tema_id
                """, codigo, usuarioId, temaId);
    }

    public void retirarVoto(UUID codigo, UUID usuarioId) {
        jdbc.update("DELETE FROM sala_votos WHERE codigo_sala = ? AND usuario_id = ?", codigo, usuarioId);
    }

    public List<TotalVotos> contarVotos(UUID codigo) {
        return jdbc.query("""
                SELECT v.tema_id, COUNT(*) AS votos
                FROM sala_votos v
                JOIN temas t ON t.id = v.tema_id
                JOIN categorias c ON c.id = t.categoria_id
                WHERE v.codigo_sala = ? AND t.status = 'APROVADO' AND c.status = 'APROVADA'
                GROUP BY v.tema_id
                ORDER BY votos DESC, v.tema_id
                """, (rs, row) -> new TotalVotos(rs.getObject("tema_id", UUID.class), rs.getLong("votos")), codigo);
    }

    public Optional<UUID> buscarVoto(UUID codigo, UUID usuarioId) {
        return jdbc.query("SELECT tema_id FROM sala_votos WHERE codigo_sala = ? AND usuario_id = ?",
                (rs, row) -> rs.getObject("tema_id", UUID.class), codigo, usuarioId).stream().findFirst();
    }

    public void adicionarParticipante(UUID codigo, UUID usuarioId) {
        jdbc.update("""
                INSERT INTO sala_participantes (codigo_sala, usuario_id) VALUES (?, ?)
                ON CONFLICT (codigo_sala, usuario_id) DO NOTHING
                """, codigo, usuarioId);
    }

    public boolean possuiParticipante(UUID codigo, UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM sala_participantes WHERE codigo_sala = ? AND usuario_id = ?
                )
                """, Boolean.class, codigo, usuarioId));
    }

    public List<Participante> listarParticipantes(UUID codigo) {
        return jdbc.query("""
                SELECT u.id, u.nome
                FROM sala_participantes p
                JOIN usuarios u ON u.id = p.usuario_id
                WHERE p.codigo_sala = ?
                ORDER BY p.entrou_em, u.id
                """, (rs, row) -> new Participante(rs.getObject("id", UUID.class), rs.getString("nome")), codigo);
    }
}
