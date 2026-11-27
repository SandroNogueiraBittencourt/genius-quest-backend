package br.com.geniusquest.repository;

import br.com.geniusquest.entity.TokenRecuperacaoSenha;
import br.com.geniusquest.entity.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenRecuperacaoSenhaRepositoryTest {
    @Autowired TokenRecuperacaoSenhaRepository tokens;
    @Autowired UsuarioRepository usuarios;
    @Autowired EntityManager entityManager;

    @Test
    void identificaTitularPeloHashDoCodigo() {
        Usuario titular = criarUsuario();
        TokenRecuperacaoSenha token = criarToken(titular);
        entityManager.clear();

        assertEquals(titular.getId(), tokens.buscarUsuarioIdPorHash(token.getTokenHash()).orElseThrow());
        assertTrue(tokens.buscarUsuarioIdPorHash(UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    void invalidaTodosOsCodigosDoTitularEPreservaOsDeOutraConta() {
        Usuario titular = criarUsuario();
        TokenRecuperacaoSenha primeiro = criarToken(titular);
        TokenRecuperacaoSenha segundo = criarToken(titular);
        TokenRecuperacaoSenha outraConta = criarToken(criarUsuario());

        tokens.invalidarTokensAbertos(titular.getId());
        entityManager.clear();

        assertTrue(tokens.findByTokenHash(primeiro.getTokenHash()).orElseThrow().getUsado());
        assertTrue(tokens.findByTokenHash(segundo.getTokenHash()).orElseThrow().getUsado());
        assertFalse(tokens.findByTokenHash(outraConta.getTokenHash()).orElseThrow().getUsado());
    }

    private Usuario criarUsuario() {
        var usuario = new Usuario();
        usuario.setNome("Pessoa Teste");
        usuario.setEmail("recuperacao-" + UUID.randomUUID() + "@exemplo.test");
        usuario.setSenha("hash-apenas-teste");
        return usuarios.saveAndFlush(usuario);
    }

    private TokenRecuperacaoSenha criarToken(Usuario titular) {
        var token = new TokenRecuperacaoSenha();
        token.setUsuario(titular);
        token.setTokenHash(UUID.randomUUID().toString());
        token.setExpiraEm(Instant.parse("2026-10-01T12:30:00Z"));
        return tokens.saveAndFlush(token);
    }
}
