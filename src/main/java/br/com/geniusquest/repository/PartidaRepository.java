package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Partida;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PartidaRepository extends JpaRepository<Partida, UUID> {
    List<Partida> findTop20ByCriador_IdOrderByCriadoEmDescIdDesc(UUID criadorId);
}
