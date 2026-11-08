package br.com.geniusquest.repository;

import br.com.geniusquest.entity.CicloVotacao;
import br.com.geniusquest.entity.StatusCicloVotacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CicloVotacaoRepository extends JpaRepository<CicloVotacao, UUID> {
    Optional<CicloVotacao> findFirstByStatusAndInicioEmLessThanEqualAndFimEmGreaterThanOrderByInicioEmAsc(
            StatusCicloVotacao status, Instant agoraInicio, Instant agoraFim);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CicloVotacao c where c.id = :id")
    Optional<CicloVotacao> buscarParaEncerramento(@org.springframework.data.repository.query.Param("id") UUID id);
}
