package br.com.geniusquest.repository;

import br.com.geniusquest.entity.RespostaPartida;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RespostaPartidaRepository extends JpaRepository<RespostaPartida, UUID> {
    boolean existsByPerguntaPartida_IdAndUsuario_Id(UUID perguntaPartidaId, UUID usuarioId);
    List<RespostaPartida> findByPerguntaPartida_Partida_IdAndUsuario_IdOrderByRespondidaEmAscIdAsc(
            UUID partidaId, UUID usuarioId);
}
