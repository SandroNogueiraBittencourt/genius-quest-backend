package br.com.geniusquest.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.geniusquest.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID>{
    Optional<Categoria> findByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCase(String nome);

    List<Categoria> findByAtivoTrueOrderByNomeAsc();
    
}
