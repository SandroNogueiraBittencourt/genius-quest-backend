package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Pergunta;
import br.com.geniusquest.entity.StatusPergunta;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerguntaRepository extends JpaRepository<Pergunta, UUID> {
    boolean existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
            UUID temaId, String enunciadoNormalizado, Collection<StatusPergunta> status);

    @Query("""
            select p.enunciado from Pergunta p
            where p.tema.id = :temaId and p.status in :status
            order by p.criadoEm desc, p.id desc
            """)
    List<String> buscarEnunciadosRecentes(@Param("temaId") UUID temaId,
            @Param("status") Collection<StatusPergunta> status, Pageable limite);

    List<Pergunta> findByTema_IdAndStatusOrderByCriadoEmAsc(
            UUID temaId, StatusPergunta status);

    // Limitada no banco para evitar expor todo o banco de questões a um jogador.
    List<Pergunta> findTop10ByTema_IdAndStatusOrderByCriadoEmAscIdAsc(
            UUID temaId, StatusPergunta status);
}
