package br.com.geniusquest.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Test
    void buscaNomeSemDiferenciarCaixaMesmoParaCategoriaRemovida() {
        String nome = nomeUnico("Tecnologia");
        assertFalse(categoriaRepository.existsByNomeIgnoreCase(nome));

        criar(nome, StatusCategoria.REMOVIDA);

        assertTrue(categoriaRepository.existsByNomeIgnoreCase(nome.toUpperCase(Locale.ROOT)));
    }

    @Test
    void paginaCategoriasComFiltroDeStatusEOrdenacao() {
        String prefixo = nomeUnico("Pagina");
        Categoria segunda = criar(prefixo + " B", StatusCategoria.APROVADA);
        Categoria primeira = criar(prefixo + " A", StatusCategoria.APROVADA);
        criar(prefixo + " AA", StatusCategoria.PENDENTE);
        var esperadas = categoriaRepository.findByStatusOrderByNomeAsc(StatusCategoria.APROVADA);
        int indice = esperadas.indexOf(primeira);
        Sort ordem = Sort.by("nome", "id");

        var pagina = categoriaRepository.findByStatus(StatusCategoria.APROVADA, PageRequest.of(indice, 1, ordem));
        var seguinte = categoriaRepository.findByStatus(StatusCategoria.APROVADA, pagina.nextPageable());

        assertEquals(List.of(primeira.getId()), pagina.map(Categoria::getId).getContent());
        assertEquals(List.of(segunda.getId()), seguinte.map(Categoria::getId).getContent());
        assertEquals(esperadas.size(), pagina.getTotalElements());
    }
}
