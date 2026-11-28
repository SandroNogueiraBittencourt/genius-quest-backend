package br.com.geniusquest.service;

import br.com.geniusquest.exception.RegraTemasException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class SalaConcorrenciaIntegrationTest {
    @Autowired SalaService salas;
    @Autowired JdbcTemplate jdbc;

    private final UUID codigo = UUID.randomUUID();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void prepararLobby() {
        criarUsuario();
        UUID anfitriao = usuarios.get(0);
        jdbc.update("INSERT INTO salas(codigo, anfitriao_id) VALUES (?, ?)", codigo, anfitriao);
        adicionarParticipante(anfitriao);
    }

    @AfterEach
    void limparDados() {
        jdbc.update("DELETE FROM sala_participantes WHERE codigo_sala = ?", codigo);
        jdbc.update("DELETE FROM salas WHERE codigo = ?", codigo);
        for (UUID usuario : usuarios) {
            jdbc.update("DELETE FROM usuarios WHERE id = ?", usuario);
        }
    }

    @Test
    void apenasUmConvidadoOcupaUltimaVaga() throws Exception {
        for (int i = 1; i < 13; i++) {
            criarUsuario();
            adicionarParticipante(usuarios.get(i));
        }
        String primeiro = criarUsuario();
        String segundo = criarUsuario();

        List<HttpStatus> respostas = entradasSimultaneas(primeiro, segundo);

        assertEquals(1, respostas.stream().filter(HttpStatus.OK::equals).count());
        assertEquals(1, respostas.stream().filter(HttpStatus.CONFLICT::equals).count());
        assertEquals(14, totalParticipantes());
    }

    @Test
    void duasEntradasDoMesmoConvidadoCriamUmaUnicaParticipacao() throws Exception {
        String convidado = criarUsuario();

        assertEquals(List.of(HttpStatus.OK, HttpStatus.OK), entradasSimultaneas(convidado, convidado));
        assertEquals(2, totalParticipantes());
    }

    private List<HttpStatus> entradasSimultaneas(String primeiro, String segundo) throws Exception {
        var inicio = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeira = executor.submit(() -> entrar(primeiro, inicio));
            var segunda = executor.submit(() -> entrar(segundo, inicio));
            return List.of(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpStatus entrar(String email, CyclicBarrier inicio) throws Exception {
        inicio.await(5, TimeUnit.SECONDS);
        try {
            salas.entrar(codigo, email);
            return HttpStatus.OK;
        } catch (RegraTemasException erro) {
            return erro.getStatus();
        }
    }

    private String criarUsuario() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.test";
        jdbc.update("INSERT INTO usuarios(id, nome, email, senha) VALUES (?, 'Pessoa Teste', ?, 'hash')", id, email);
        usuarios.add(id);
        return email;
    }

    private void adicionarParticipante(UUID id) {
        jdbc.update("INSERT INTO sala_participantes(codigo_sala, usuario_id) VALUES (?, ?)", codigo, id);
    }

    private Integer totalParticipantes() {
        return jdbc.queryForObject("SELECT count(*) FROM sala_participantes WHERE codigo_sala = ?", Integer.class, codigo);
    }
}
