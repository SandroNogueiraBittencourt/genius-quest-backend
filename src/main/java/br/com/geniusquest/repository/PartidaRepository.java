package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Partida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PartidaRepository extends JpaRepository<Partida, UUID> {
    List<Partida> findTop20ByCriador_IdOrderByCriadoEmDescIdDesc(UUID criadorId);

    Page<Partida> findByCriador_Id(UUID criadorId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Partida p where p.id = :id")
    Optional<Partida> findByIdParaAtualizacao(@Param("id") UUID id);
}
