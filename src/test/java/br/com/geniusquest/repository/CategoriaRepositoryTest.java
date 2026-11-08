package br.com.geniusquest.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    // Evita colisões com categorias já cadastradas em bancos locais persistentes.
    private String nomeUnico(String nome) {
        return "Teste-" + UUID.randomUUID().toString().substring(0, 8) + "-" + nome;
    }

    private Categoria criar(String nome, StatusCategoria status) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setDescricao("Descrição de " + nome);
        categoria.setStatus(status);
        return categoriaRepository.saveAndFlush(categoria);
    }

    @Test
    void deveSalvarCategoria() {
        Categoria salva = criar(nomeUnico("Tecnologia"), null);

        assertNotNull(salva.getId());
        assertEquals(StatusCategoria.PENDENTE, salva.getStatus());
        assertNotNull(salva.getCriadoEm());
        assertNotNull(salva.getAtualizadoEm());
    }

    @Test
    void deveListarSomenteCategoriasComOStatusPedidoEmOrdemAlfabetica() {
        String prefixo = "Teste-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        Categoria ciencia = criar(prefixo + "Ciencia", StatusCategoria.APROVADA);
        criar(prefixo + "Esportes", StatusCategoria.PENDENTE);
        Categoria arte = criar(prefixo + "Arte", StatusCategoria.APROVADA);
        criar(prefixo + "Geografia", StatusCategoria.REMOVIDA);

        Set<UUID> idsDesteTeste = Set.of(arte.getId(), ciencia.getId());
        List<Categoria> aprovadasDesteTeste = categoriaRepository
                .findByStatusOrderByNomeAsc(StatusCategoria.APROVADA)
                .stream()
                .filter(categoria -> idsDesteTeste.contains(categoria.getId()))
                .toList();

        assertEquals(2, aprovadasDesteTeste.size());
        assertEquals(prefixo + "Arte", aprovadasDesteTeste.get(0).getNome());
        assertEquals(prefixo + "Ciencia", aprovadasDesteTeste.get(1).getNome());
    }

    @Test
    void naoDeveSalvarNomeDuplicadoIgnorandoMaiusculasEMinusculas() {
        String nome = nomeUnico("Tecnologia");
        criar(nome, StatusCategoria.APROVADA);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> criar(nome.toLowerCase(Locale.ROOT), StatusCategoria.PENDENTE)
        );
    }

    @Test
    void naoDeveSalvarNomeDuplicado() {
        String nome = nomeUnico("Tecnologia");
        criar(nome, StatusCategoria.APROVADA);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> criar(nome, StatusCategoria.PENDENTE)
        );
    }
}
