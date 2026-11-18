package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Pergunta;
import br.com.geniusquest.entity.StatusPergunta;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerguntaRepository extends JpaRepository<Pergunta, UUID> {
    boolean existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
            UUID temaId, String enunciadoNormalizado, Collection<StatusPergunta> status);

    List<Pergunta> findByTema_IdAndStatusOrderByCriadoEmAsc(
            UUID temaId, StatusPergunta status);
}
