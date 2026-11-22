package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Partida;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartidaRepository extends JpaRepository<Partida, UUID> {
    List<Partida> findTop20ByCriador_IdOrderByCriadoEmDescIdDesc(UUID criadorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Partida p where p.id = :id")
    Optional<Partida> findByIdParaAtualizacao(@Param("id") UUID id);

    default Optional<Partida> buscarParaAtualizacao(UUID id) {
        return findByIdParaAtualizacao(id);
    }
}