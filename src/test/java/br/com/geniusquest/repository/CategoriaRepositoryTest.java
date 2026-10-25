package br.com.geniusquest.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;
import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles ("test")
@Transactional 
public class CategoriaRepositoryTest {
    
    @Autowired
    private CategoriaRepository categoriaRepository;

    private Categoria criar(String nome, StatusCategoria status) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setDescricao("Descrição de " + nome);
        categoria.setStatus(status);
        return categoriaRepository.saveAndFlush(categoria);
    }

    @Test 
    void deveSalvarCategoria() {
        Categoria salva = criar("Tecnologia", null);

        assertNotNull(salva.getId());
        assertEquals(StatusCategoria.PENDENTE, salva.getStatus());
        assertNotNull(salva.getCriadoEm());
        assertNotNull(salva.getAtualizadoEm());
    }

    @Test
    void deveListarSomenteCategoriasComOStatusPedidoEmOrdemAlfabetica() {
        criar("Ciência", StatusCategoria.APROVADA);
        criar("Esportes", StatusCategoria.PENDENTE);
        criar("Arte", StatusCategoria.APROVADA);
        criar("Geografia", StatusCategoria.REMOVIDA);

        List<Categoria> aprovadas =
                categoriaRepository.findByStatusOrderByNomeAsc(StatusCategoria.APROVADA);

        assertEquals(2, aprovadas.size());
        assertEquals("Arte", aprovadas.get(0).getNome());
        assertEquals("Ciência", aprovadas.get(1).getNome());
    }

    @Test
    void naoDeveSalvarNomeDuplicadoIgnorandoMaiusculasEMinusculas() {

        criar("Tecnologia", StatusCategoria.APROVADA);

        assertThrows(
            DataIntegrityViolationException.class,
            () -> criar("tecnologia", StatusCategoria.PENDENTE)
        );
    }
    
    @Test
    void naoDeveSalvarNomeDuplicado() {
        criar("Tecnologia", StatusCategoria.APROVADA);

        assertThrows(DataIntegrityViolationException.class,
                () -> criar("Tecnologia", StatusCategoria.PENDENTE));
    }
}
