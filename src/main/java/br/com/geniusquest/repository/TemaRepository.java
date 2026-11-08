package br.com.geniusquest.repository;

import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TemaRepository extends JpaRepository<Tema, UUID> {
    boolean existsByCategoria_IdAndTituloNormalizadoAndStatusNot(
            UUID categoriaId, String tituloNormalizado, StatusTema status);
    List<Tema> findByStatusOrderByCriadoEmAsc(StatusTema status);
    List<Tema> findAllByOrderByCriadoEmAsc();
}
