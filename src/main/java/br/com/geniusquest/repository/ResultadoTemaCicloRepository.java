package br.com.geniusquest.repository;

import br.com.geniusquest.entity.ResultadoTemaCiclo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultadoTemaCicloRepository extends JpaRepository<ResultadoTemaCiclo, UUID> {
    List<ResultadoTemaCiclo> findByCicloIdOrderByPosicaoAsc(UUID cicloId);
    Optional<ResultadoTemaCiclo> findByCicloIdAndTemaId(UUID cicloId, UUID temaId);
}
