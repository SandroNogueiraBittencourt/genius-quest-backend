package br.com.geniusquest.repository;

import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemaRepositoryTest {
    @Autowired TemaRepository temas;
    @Autowired JdbcTemplate jdbc;

    @Test
    void verificaTituloSomenteNaCategoriaEIgnoraTemaRemovido() {
        UUID autor = autor();
        UUID categoria = categoria("APROVADA");
        UUID outraCategoria = categoria("APROVADA");
        String titulo = "tema-" + UUID.randomUUID();
        tema(autor, categoria, titulo, "REMOVIDO");
        assertFalse(temas.existsByCategoria_IdAndTituloNormalizadoAndStatusNot(categoria, titulo, StatusTema.REMOVIDO));

        tema(autor, categoria, titulo, "PENDENTE");
        assertTrue(temas.existsByCategoria_IdAndTituloNormalizadoAndStatusNot(categoria, titulo, StatusTema.REMOVIDO));
        assertFalse(temas.existsByCategoria_IdAndTituloNormalizadoAndStatusNot(outraCategoria, titulo, StatusTema.REMOVIDO));
    }

    @Test
    void excluiCategoriasPendentesETemasSuspensosDaPaginaDeAprovados() {
        UUID autor = autor();
        UUID aprovada = categoria("APROVADA");
        UUID pendente = categoria("PENDENTE");
        UUID publicado = tema(autor, aprovada, "publicado-" + UUID.randomUUID(), "APROVADO");
        UUID suspenso = tema(autor, aprovada, "suspenso-" + UUID.randomUUID(), "SUSPENSO");
        UUID oculto = tema(autor, pendente, "oculto-" + UUID.randomUUID(), "APROVADO");

        var pagina = temas.findByStatusAndCategoria_Status(StatusTema.APROVADO, StatusCategoria.APROVADA,
                PageRequest.of(0, 100, Sort.by("criadoEm").descending().and(Sort.by("id"))));
        var ids = pagina.getContent().stream().map(Tema::getId).toList();
        assertTrue(ids.contains(publicado));
        assertFalse(ids.contains(suspenso));
        assertFalse(ids.contains(oculto));
    }

    private UUID autor() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuarios(id, nome, email, senha) VALUES (?, ?, ?, ?)",
                id, "Autor Teste", id + "@example.test", "hash");
        return id;
    }

    private UUID categoria(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categorias(id, nome, status) VALUES (?, ?, ?)", id, "Categoria " + id, status);
        return id;
    }

    private UUID tema(UUID autor, UUID categoria, String titulo, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO temas(id, categoria_id, autor_id, titulo, titulo_normalizado, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, categoria, autor, titulo, titulo, status);
        return id;
    }
}
