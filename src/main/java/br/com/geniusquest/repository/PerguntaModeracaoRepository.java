package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Pergunta;
import br.com.geniusquest.entity.StatusPergunta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas exclusivamente administrativas, separadas do repositório do fluxo de geração. */
public interface PerguntaModeracaoRepository extends JpaRepository<Pergunta, UUID> {
    Page<Pergunta> findByStatus(StatusPergunta status, Pageable pageable);
    Page<Pergunta> findByTema_Id(UUID temaId, Pageable pageable);
    Page<Pergunta> findByTema_IdAndStatus(UUID temaId, StatusPergunta status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pergunta p where p.id = :id")
    Optional<Pergunta> buscarParaAlterar(@Param("id") UUID id);
}
