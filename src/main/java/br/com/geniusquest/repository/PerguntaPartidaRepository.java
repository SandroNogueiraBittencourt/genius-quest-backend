package br.com.geniusquest.repository;

import br.com.geniusquest.entity.PerguntaPartida;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerguntaPartidaRepository extends JpaRepository<PerguntaPartida, UUID> {
    List<PerguntaPartida> findByPartida_IdOrderByOrdemAsc(UUID partidaId);
    Optional<PerguntaPartida> findByPartida_IdAndPergunta_Id(UUID partidaId, UUID perguntaId);
}
