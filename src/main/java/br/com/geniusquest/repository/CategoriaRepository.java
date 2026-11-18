package br.com.geniusquest.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID>{
    boolean existsByNomeIgnoreCase(String nome);

    Page<Categoria> findByStatusOrderByNomeAsc(StatusCategoria status, Pageable pageable);
    
}
