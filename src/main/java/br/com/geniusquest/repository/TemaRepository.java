package br.com.geniusquest.repository;

import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TemaRepository extends JpaRepository<Tema, UUID> {
    boolean existsByCategoria_IdAndTituloNormalizadoAndStatusNot(
            UUID categoriaId, String tituloNormalizado, StatusTema status);
    List<Tema> findByStatusOrderByCriadoEmAsc(StatusTema status);
    List<Tema> findAllByOrderByCriadoEmAsc();

    Page<Tema> findByStatusAndCategoria_Status(
            StatusTema status, StatusCategoria categoriaStatus, Pageable pageable);
}
