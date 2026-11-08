package br.com.geniusquest.repository;

import br.com.geniusquest.entity.VotoTema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface VotoTemaRepository extends JpaRepository<VotoTema, UUID> {
    boolean existsByUsuario_IdAndTema_IdAndCiclo_Id(UUID usuarioId, UUID temaId, UUID cicloId);
    @Query("select v.tema.id as temaId, count(v) as total from VotoTema v where v.ciclo.id = :cicloId group by v.tema.id")
    List<ContagemTema> contarPorTemaNoCiclo(@Param("cicloId") UUID cicloId);

    interface ContagemTema {
        UUID getTemaId();
        Long getTotal();
    }
}
