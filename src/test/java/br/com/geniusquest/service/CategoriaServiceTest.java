package br.com.geniusquest.service;

import br.com.geniusquest.dto.categoria.CategoriaAtualizacaoDTO;
import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.entity.Categoria;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.exception.CategoriaJaCadastradaException;
import br.com.geniusquest.exception.CategoriaNaoEncontradaException;
import br.com.geniusquest.mapper.CategoriaMapper;
import br.com.geniusquest.repository.CategoriaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository repository;

    @Mock
    private CategoriaMapper mapper;

    @InjectMocks
    private CategoriaService service;

    private UUID categoriaId;
    private Categoria categoria;

    @BeforeEach
    void setUp() {

        categoriaId = UUID.randomUUID();

        categoria = new Categoria();
        categoria.setId(categoriaId);
        categoria.setNome("Tecnologia");
        categoria.setDescricao("Categoria de tecnologia");
        categoria.setStatus(StatusCategoria.PENDENTE);
    }

    private CategoriaResponseDTO criarResposta(Categoria categoria) {

        return new CategoriaResponseDTO(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getStatus(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void deveCadastrarCategoriaComSucesso() {

        CategoriaCadastroDTO dto =
                new CategoriaCadastroDTO(
                        "Tecnologia",
                        "Categoria de tecnologia"
                );

        CategoriaResponseDTO resposta =
                criarResposta(categoria);

        when(repository.existsByNomeIgnoreCase("Tecnologia"))
                .thenReturn(false);

        when(mapper.toEntity(dto))
                .thenReturn(categoria);

        when(repository.save(categoria))
                .thenReturn(categoria);

        when(mapper.toResponse(categoria))
                .thenReturn(resposta);

        CategoriaResponseDTO resultado =
                service.cadastrar(dto);

        assertNotNull(resultado);
        assertEquals(categoriaId, resultado.id());
        assertEquals("Tecnologia", resultado.nome());
        assertEquals(
                StatusCategoria.PENDENTE,
                resultado.status()
        );

        verify(repository)
                .existsByNomeIgnoreCase("Tecnologia");

        verify(repository)
                .save(categoria);
    }

    @Test
    void naoDeveCadastrarCategoriaComNomeDuplicado() {

        CategoriaCadastroDTO dto =
                new CategoriaCadastroDTO(
                        "Tecnologia",
                        "Categoria duplicada"
                );

        when(repository.existsByNomeIgnoreCase("Tecnologia"))
                .thenReturn(true);

        assertThrows(
                CategoriaJaCadastradaException.class,
                () -> service.cadastrar(dto)
        );

        verify(mapper, never())
                .toEntity(any());

        verify(repository, never())
                .save(any());
    }

    @Test
    void deveBuscarCategoriaPorId() {

        CategoriaResponseDTO resposta =
                criarResposta(categoria);

        when(repository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));

        when(mapper.toResponse(categoria))
                .thenReturn(resposta);

        CategoriaResponseDTO resultado =
                service.buscarPorId(categoriaId);

        assertNotNull(resultado);
        assertEquals(categoriaId, resultado.id());
        assertEquals("Tecnologia", resultado.nome());
    }

    @Test
    void deveLancarExcecaoQuandoCategoriaNaoExistir() {

        when(repository.findById(categoriaId))
                .thenReturn(Optional.empty());

        assertThrows(
                CategoriaNaoEncontradaException.class,
                () -> service.buscarPorId(categoriaId)
        );

        verify(mapper, never())
                .toResponse(any());
    }

    @Test
    void deveListarTodasAsCategorias() {

        Categoria outraCategoria = new Categoria();
        outraCategoria.setId(UUID.randomUUID());
        outraCategoria.setNome("Ciência");
        outraCategoria.setDescricao("Categoria de ciência");
        outraCategoria.setStatus(StatusCategoria.APROVADA);

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                categoria,
                                outraCategoria
                        )
                );

        when(mapper.toResponse(categoria))
                .thenReturn(criarResposta(categoria));

        when(mapper.toResponse(outraCategoria))
                .thenReturn(criarResposta(outraCategoria));

        List<CategoriaResponseDTO> resultado =
                service.listar();

        assertEquals(2, resultado.size());

        verify(repository)
                .findAll();
    }

    @Test
    void deveListarCategoriasAprovadas() {

        categoria.setStatus(StatusCategoria.APROVADA);

        when(repository.findByStatusOrderByNomeAsc(
                StatusCategoria.APROVADA
        )).thenReturn(List.of(categoria));

        when(mapper.toResponse(categoria))
                .thenReturn(criarResposta(categoria));

        List<CategoriaResponseDTO> resultado =
                service.listarAprovadas();

        assertEquals(1, resultado.size());

        assertEquals(
                StatusCategoria.APROVADA,
                resultado.get(0).status()
        );

        verify(repository)
                .findByStatusOrderByNomeAsc(
                        StatusCategoria.APROVADA
                );
    }

    @Test
    void deveListarCategoriasPendentes() {

        when(repository.findByStatusOrderByNomeAsc(
                StatusCategoria.PENDENTE
        )).thenReturn(List.of(categoria));

        when(mapper.toResponse(categoria))
                .thenReturn(criarResposta(categoria));

        List<CategoriaResponseDTO> resultado =
                service.listarPendentes();

        assertEquals(1, resultado.size());

        assertEquals(
                StatusCategoria.PENDENTE,
                resultado.get(0).status()
        );
    }

    @Test
    void deveListarCategoriasRemovidas() {

        categoria.setStatus(StatusCategoria.REMOVIDA);

        when(repository.findByStatusOrderByNomeAsc(
                StatusCategoria.REMOVIDA
        )).thenReturn(List.of(categoria));

        when(mapper.toResponse(categoria))
                .thenReturn(criarResposta(categoria));

        List<CategoriaResponseDTO> resultado =
                service.listarRemovidas();

        assertEquals(1, resultado.size());

        assertEquals(
                StatusCategoria.REMOVIDA,
                resultado.get(0).status()
        );
    }

    @Test
    void deveAtualizarNomeEDescricaoDaCategoria() {

        CategoriaAtualizacaoDTO dto =
                new CategoriaAtualizacaoDTO(
                        "  Ciência  ",
                        "  Categoria de ciência  "
                );

        when(repository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));

        when(repository.existsByNomeIgnoreCase("Ciência"))
                .thenReturn(false);

        when(mapper.toResponse(categoria))
                .thenAnswer(invocation ->
                        criarResposta(
                                invocation.getArgument(0)
                        )
                );

        CategoriaResponseDTO resultado =
                service.atualizar(
                        categoriaId,
                        dto
                );

        assertEquals("Ciência", resultado.nome());

        assertEquals(
                "Categoria de ciência",
                resultado.descricao()
        );

        verify(repository)
                .existsByNomeIgnoreCase("Ciência");
    }

    @Test
    void naoDeveAtualizarParaNomeJaCadastrado() {

        CategoriaAtualizacaoDTO dto =
                new CategoriaAtualizacaoDTO(
                        "Ciência",
                        null
                );

        when(repository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));

        when(repository.existsByNomeIgnoreCase("Ciência"))
                .thenReturn(true);

        assertThrows(
                CategoriaJaCadastradaException.class,
                () -> service.atualizar(
                        categoriaId,
                        dto
                )
        );

        assertEquals(
                "Tecnologia",
                categoria.getNome()
        );

        verify(mapper, never())
                .toResponse(any());
    }

    @Test
    void deveAprovarCategoria() {

        when(repository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));

        when(mapper.toResponse(categoria))
                .thenAnswer(invocation ->
                        criarResposta(
                                invocation.getArgument(0)
                        )
                );

        CategoriaResponseDTO resultado =
                service.aprovar(categoriaId);

        assertEquals(
                StatusCategoria.APROVADA,
                resultado.status()
        );

        assertEquals(
                StatusCategoria.APROVADA,
                categoria.getStatus()
        );
    }

    @Test
    void deveRemoverCategoria() {

        categoria.setStatus(StatusCategoria.APROVADA);

        when(repository.findById(categoriaId))
                .thenReturn(Optional.of(categoria));

        when(mapper.toResponse(categoria))
                .thenAnswer(invocation ->
                        criarResposta(
                                invocation.getArgument(0)
                        )
                );

        CategoriaResponseDTO resultado =
                service.remover(categoriaId);

        assertEquals(
                StatusCategoria.REMOVIDA,
                resultado.status()
        );

        assertEquals(
                StatusCategoria.REMOVIDA,
                categoria.getStatus()
        );
    }
}
