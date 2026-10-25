package br.com.geniusquest.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID>{
    boolean existsByNomeIgnoreCase(String nome);

    List<Categoria> findByStatusOrderByNomeAsc(StatusCategoria status);
    
}
