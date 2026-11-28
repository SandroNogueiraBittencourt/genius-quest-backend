package br.com.geniusquest.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VotoTemaRepositoryTest {
    @Autowired VotoTemaRepository votos;
    @Autowired JdbcTemplate jdbc;

    private UUID autor;
    private UUID outroUsuario;
    private UUID primeiroTema;
    private UUID segundoTema;
    private UUID primeiroCiclo;
    private UUID segundoCiclo;

    @BeforeEach
    void prepararVotos() {
        autor = usuario();
        outroUsuario = usuario();
        UUID categoria = UUID.randomUUID();
        jdbc.update("INSERT INTO categorias(id, nome) VALUES (?, ?)", categoria, "Categoria " + categoria);
        primeiroTema = tema(categoria, "Primeiro");
        segundoTema = tema(categoria, "Segundo");
        primeiroCiclo = ciclo();
        segundoCiclo = ciclo();
        votar(autor, primeiroTema, primeiroCiclo);
        votar(outroUsuario, primeiroTema, primeiroCiclo);
        votar(autor, segundoTema, primeiroCiclo);
        votar(outroUsuario, segundoTema, segundoCiclo);
    }

    @Test
    void verificaVotoPelaCombinacaoDeUsuarioTemaECiclo() {
        assertTrue(votos.existsByUsuario_IdAndTema_IdAndCiclo_Id(autor, segundoTema, primeiroCiclo));
        assertFalse(votos.existsByUsuario_IdAndTema_IdAndCiclo_Id(autor, segundoTema, segundoCiclo));
        assertFalse(votos.existsByUsuario_IdAndTema_IdAndCiclo_Id(outroUsuario, segundoTema, primeiroCiclo));
    }

    @Test
    void agregaVotosPorTemaSomenteNoCicloConsultado() {
        var totais = votos.contarPorTemaNoCiclo(primeiroCiclo).stream().collect(Collectors.toMap(
                VotoTemaRepository.ContagemTema::getTemaId, VotoTemaRepository.ContagemTema::getTotal));
        assertEquals(Map.of(primeiroTema, 2L, segundoTema, 1L), totais);
    }

    @Test
    void contaVotosSomenteNosCiclosSolicitadosEComVotos() {
        UUID cicloVazio = ciclo();
        var totais = votos.contarPorCiclos(List.of(segundoCiclo, cicloVazio)).stream().collect(Collectors.toMap(
                VotoTemaRepository.ContagemCiclo::getCicloId, VotoTemaRepository.ContagemCiclo::getTotal));
        assertEquals(Map.of(segundoCiclo, 1L), totais);
    }

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuarios(id, nome, email, senha) VALUES (?, 'Pessoa Teste', ?, 'hash')",
                id, id + "@example.test");
        return id;
    }

    private UUID tema(UUID categoria, String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO temas(id, categoria_id, autor_id, titulo, titulo_normalizado)
                VALUES (?, ?, ?, ?, lower(?))
                """, id, categoria, autor, titulo, titulo);
        return id;
    }

    private UUID ciclo() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ciclos_votacao(id, nome, inicio_em, fim_em, status)
                VALUES (?, 'Ciclo de teste', now() - interval '2 hours', now() - interval '1 hour', 'ENCERRADO')
                """, id);
        return id;
    }

    private void votar(UUID usuario, UUID tema, UUID ciclo) {
        jdbc.update("INSERT INTO votos_tema(id, usuario_id, tema_id, ciclo_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), usuario, tema, ciclo);
    }
}
