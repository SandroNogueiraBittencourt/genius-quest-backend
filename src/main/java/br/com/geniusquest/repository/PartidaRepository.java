package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Partida;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartidaRepository extends JpaRepository<Partida, UUID> {
    String VISIBILIDADE_HISTORICO = """
            FROM partidas p
            WHERE p.criador_id = :usuarioId
               OR EXISTS (SELECT 1 FROM resultados_partida r
                          WHERE r.partida_id = p.id AND r.usuario_id = :usuarioId)
               OR EXISTS (
                   SELECT 1 FROM salas s
                   JOIN sala_participantes participante ON participante.codigo_sala = s.codigo
                   WHERE s.partida_id = p.id AND participante.usuario_id = :usuarioId
               )
            """;
    String ORDEM_HISTORICO = " ORDER BY p.criado_em DESC, p.id DESC";

    @Query(value = "SELECT p.* " + VISIBILIDADE_HISTORICO + ORDEM_HISTORICO + " LIMIT 20", nativeQuery = true)
    List<Partida> listarMinhasComSalas(@Param("usuarioId") UUID usuarioId);

    @Query(value = "SELECT p.* " + VISIBILIDADE_HISTORICO + ORDEM_HISTORICO,
            countQuery = "SELECT COUNT(*) " + VISIBILIDADE_HISTORICO, nativeQuery = true)
    Page<Partida> listarMinhasComSalas(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    List<Partida> findTop20ByCriador_IdAndCicloIsNotNullOrderByCriadoEmDescIdDesc(UUID criadorId);

    Page<Partida> findByCriador_IdAndCicloIsNotNullOrderByCriadoEmDescIdDesc(UUID criadorId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Partida p where p.id = :id")
    Optional<Partida> findByIdParaAtualizacao(@Param("id") UUID id);
}
