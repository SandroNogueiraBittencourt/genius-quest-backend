package br.com.geniusquest.repository;

import br.com.geniusquest.entity.RespostaPartida;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RespostaPartidaRepository extends JpaRepository<RespostaPartida, UUID> {
    boolean existsByPerguntaPartida_IdAndUsuario_Id(UUID perguntaPartidaId, UUID usuarioId);
    @Query("select r from RespostaPartida r where r.perguntaPartida.partida.id = :partidaId "
            + "and r.usuario.id = :usuarioId order by r.respondidaEm asc, r.id asc")
    List<RespostaPartida> findByPerguntaPartida_Partida_IdAndUsuario_IdOrderByRespondidaEmAscIdAsc(
            @Param("partidaId") UUID partidaId, @Param("usuarioId") UUID usuarioId);
}
