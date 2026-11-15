package br.com.geniusquest.repository;

import br.com.geniusquest.entity.EventoCuradoriaPergunta;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoCuradoriaPerguntaRepository extends JpaRepository<EventoCuradoriaPergunta, UUID> {
    List<EventoCuradoriaPergunta> findByPergunta_IdOrderByCriadoEmAscIdAsc(UUID perguntaId);
}
