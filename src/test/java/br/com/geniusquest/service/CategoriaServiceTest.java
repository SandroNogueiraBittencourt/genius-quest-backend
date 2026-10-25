package br.com.geniusquest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaInvalidaException;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.mapper.CategoriaMapper;
import br.com.geniusquest.repository.CategoriaRepository;

@ExtendWith(MockitoExtension.class)
public class CategoriaServiceTest {
    @Mock
    private CategoriaRepository repository;

    private CategoriaService service;

    @BeforeEach
    void setUp() {
        service = new CategoriaService(repository, new CategoriaMapper());
    }

    private Categoria criarCategoria(UUID id, String nome, StatusCategoria status) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNome(nome);
        categoria.setDescricao("Descrição de " + nome);
        categoria.setStatus(status);
        return categoria;
    }

    private CategoriaDTO criarDto(String nome, String descricao, StatusCategoria status) {
        return new CategoriaDTO(null, nome, descricao, status, null, null);
    }

    @Test
    void deveCadastrarCategoriaComSucessoRemovendoEspacosDoNome() {
        when(repository.existsByNomeIgnoreCase("Tecnologia")).thenReturn(false);
        when(repository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaDTO resposta = service.cadastrar(criarDto("  Tecnologia  ", "Sobre tecnologia", null));

        assertEquals("Tecnologia", resposta.nome());
        assertEquals("Sobre tecnologia", resposta.descricao());
    }

    @Test
    void naoDeveCadastrarCategoriaDuplicada() {
        when(repository.existsByNomeIgnoreCase("Tecnologia")).thenReturn(true);

        assertThrows(CategoriaJaCadastradaException.class,
                () -> service.cadastrar(criarDto("Tecnologia", null, null)));

        verify(repository, never()).save(any(Categoria.class));
    }

    @Test
    void naoDeveAceitarStatusVindoDoClienteNoCadastro() {
        when(repository.existsByNomeIgnoreCase("Ciência")).thenReturn(false);
        when(repository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cadastrar(criarDto("Ciência", null, StatusCategoria.APROVADA));

        ArgumentCaptor<Categoria> captor = ArgumentCaptor.forClass(Categoria.class);
        verify(repository).save(captor.capture());

        // o status não é copiado; quem define PENDENTE é o @PrePersist da entidade
        assertNull(captor.getValue().getStatus());
    }

    @Test
    void deveListarCategorias() {
        when(repository.findAll()).thenReturn(List.of(
                criarCategoria(UUID.randomUUID(), "Arte", StatusCategoria.APROVADA),
                criarCategoria(UUID.randomUUID(), "História", StatusCategoria.PENDENTE)));

        List<CategoriaDTO> resposta = service.listar();

        assertEquals(2, resposta.size());
        assertEquals("Arte", resposta.get(0).nome());
    }

    @Test
    void deveListarSomenteCategoriasAprovadas() {
        when(repository.findByStatusOrderByNomeAsc(StatusCategoria.APROVADA)).thenReturn(List.of(
                criarCategoria(UUID.randomUUID(), "Arte", StatusCategoria.APROVADA)));

        List<CategoriaDTO> resposta = service.listarAprovadas();

        assertEquals(1, resposta.size());
        assertEquals(StatusCategoria.APROVADA, resposta.get(0).status());
    }

    @Test
    void deveListarSomenteCategoriasPendentes() {
        when(repository.findByStatusOrderByNomeAsc(StatusCategoria.PENDENTE)).thenReturn(List.of(
                criarCategoria(UUID.randomUUID(), "Esportes", StatusCategoria.PENDENTE)));

        List<CategoriaDTO> resposta = service.listarPendentes();

        assertEquals(1, resposta.size());
        assertEquals(StatusCategoria.PENDENTE, resposta.get(0).status());
    }

    @Test
    void deveListarSomenteCategoriasRemovidas() {
        when(repository.findByStatusOrderByNomeAsc(StatusCategoria.REMOVIDA)).thenReturn(List.of(
                criarCategoria(UUID.randomUUID(), "Geografia", StatusCategoria.REMOVIDA)));

        List<CategoriaDTO> resposta = service.listarRemovidas();

        assertEquals(1, resposta.size());
        assertEquals(StatusCategoria.REMOVIDA, resposta.get(0).status());
    }

    @Test
    void deveBuscarCategoriaPorId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(
                Optional.of(criarCategoria(id, "Tecnologia", StatusCategoria.APROVADA)));

        CategoriaDTO resposta = service.buscarPorId(id);

        assertEquals(id, resposta.id());
        assertEquals("Tecnologia", resposta.nome());
    }

    @Test
    void deveLancarExcecaoQuandoCategoriaNaoExistir() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CategoriaNaoEncontradaException.class, () -> service.buscarPorId(id));
    }

    @Test
    void deveAtualizarNomeDescricaoEStatus() {
        UUID id = UUID.randomUUID();
        Categoria existente = criarCategoria(id, "Tecnologia", StatusCategoria.PENDENTE);
        when(repository.findById(id)).thenReturn(Optional.of(existente));
        when(repository.existsByNomeIgnoreCase("Ciência")).thenReturn(false);

        CategoriaDTO resposta = service.atualizar(id,
                criarDto("  Ciência ", "Nova descrição", StatusCategoria.APROVADA));

        assertEquals("Ciência", resposta.nome());
        assertEquals("Nova descrição", resposta.descricao());
        assertEquals(StatusCategoria.APROVADA, resposta.status());
    }

    @Test
    void deveAtualizarParcialmenteSemMexerNosCamposNulos() {
        UUID id = UUID.randomUUID();
        Categoria existente = criarCategoria(id, "Tecnologia", StatusCategoria.APROVADA);
        when(repository.findById(id)).thenReturn(Optional.of(existente));

        CategoriaDTO resposta = service.atualizar(id, criarDto(null, null, StatusCategoria.REMOVIDA));

        assertEquals("Tecnologia", resposta.nome());
        assertEquals("Descrição de Tecnologia", resposta.descricao());
        assertEquals(StatusCategoria.REMOVIDA, resposta.status());
    }

    @Test
    void devePermitirManterOProprioNomeMudandoSoAsMaiusculas() {
        UUID id = UUID.randomUUID();
        Categoria existente = criarCategoria(id, "Tecnologia", StatusCategoria.APROVADA);
        when(repository.findById(id)).thenReturn(Optional.of(existente));

        CategoriaDTO resposta = service.atualizar(id, criarDto("tecnologia", null, null));

        assertEquals("tecnologia", resposta.nome());
        verify(repository, never()).existsByNomeIgnoreCase(anyString());
    }

    @Test
    void naoDeveAtualizarParaNomeJaExistente() {
        UUID id = UUID.randomUUID();
        Categoria existente = criarCategoria(id, "Tecnologia", StatusCategoria.APROVADA);
        when(repository.findById(id)).thenReturn(Optional.of(existente));
        when(repository.existsByNomeIgnoreCase("História")).thenReturn(true);

        assertThrows(CategoriaJaCadastradaException.class,
                () -> service.atualizar(id, criarDto("História", null, null)));
    }

    @Test
    void deveLancarExcecaoAoAtualizarCategoriaInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CategoriaNaoEncontradaException.class,
                () -> service.atualizar(id, criarDto("Qualquer", null, null)));
    }

    @Test
    void naoDeveCadastrarSemNome() {
        assertThrows(CategoriaInvalidaException.class,
                () -> service.cadastrar(criarDto(null, null, null)));

        verify(repository, never()).save(any(Categoria.class));
    }

    @Test
    void naoDeveCadastrarComNomeEmBranco() {
        assertThrows(CategoriaInvalidaException.class,
                () -> service.cadastrar(criarDto("   ", null, null)));
    }

    @Test
    void naoDeveCadastrarComNomeCurtoDemais() {
        assertThrows(CategoriaInvalidaException.class,
                () -> service.cadastrar(criarDto("A", null, null)));
    }

    @Test
    void naoDeveCadastrarComNomeLongoDemais() {
        assertThrows(CategoriaInvalidaException.class,
                () -> service.cadastrar(criarDto("x".repeat(81), null, null)));
    }

    @Test
    void naoDeveCadastrarComDescricaoLongaDemais() {
        assertThrows(CategoriaInvalidaException.class,
                () -> service.cadastrar(criarDto("Tecnologia", "x".repeat(256), null)));
    }

    @Test
    void naoDeveAtualizarParaNomeInvalido() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(
                Optional.of(criarCategoria(id, "Tecnologia", StatusCategoria.APROVADA)));

        assertThrows(CategoriaInvalidaException.class,
                () -> service.atualizar(id, criarDto("A", null, null)));
    }
}
