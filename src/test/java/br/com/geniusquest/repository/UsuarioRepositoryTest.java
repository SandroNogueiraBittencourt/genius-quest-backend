package br.com.geniusquest.repository;

import br.com.geniusquest.entity.Usuario;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository repository;

    @Test
    void deveSalvarUsuario() {

        Usuario usuario = new Usuario();

        usuario.setNome("Sandro Nogueira");
        usuario.setEmail("repository@geniusquest.com");
        usuario.setSenha("$2a$10$hash");

        Usuario salvo = repository.saveAndFlush(usuario);

        assertNotNull(salvo.getId());

        assertEquals(
                "Sandro Nogueira",
                salvo.getNome()
        );

        assertEquals(
                "repository@geniusquest.com",
                salvo.getEmail()
        );

        assertNotNull(salvo.getCriadoEm());
        assertNotNull(salvo.getAtualizadoEm());
    }

    @Test
    void deveBuscarUsuarioPorEmailIgnorandoMaiusculasMinusculas() {

        Usuario usuario = new Usuario();

        usuario.setNome("Sandro Nogueira");
        usuario.setEmail("sandro@geniusquest.com");
        usuario.setSenha("$2a$10$hash");

        repository.saveAndFlush(usuario);

        Optional<Usuario> encontrado =
                repository.findByEmailIgnoreCase(
                        "SANDRO@GENIUSQUEST.COM"
                );

        assertTrue(encontrado.isPresent());

        assertEquals(
                "sandro@geniusquest.com",
                encontrado.get().getEmail()
        );
    }

    @Test
    void deveRetornarTrueQuandoEmailExistir() {

        Usuario usuario = new Usuario();

        usuario.setNome("Sandro Nogueira");
        usuario.setEmail("existe@geniusquest.com");
        usuario.setSenha("$2a$10$hash");

        repository.saveAndFlush(usuario);

        boolean existe =
                repository.existsByEmailIgnoreCase(
                        "EXISTE@GENIUSQUEST.COM"
                );

        assertTrue(existe);
    }

    @Test
    void deveRetornarFalseQuandoEmailNaoExistir() {

        boolean existe =
                repository.existsByEmailIgnoreCase(
                        "naoexiste@geniusquest.com"
                );

        assertFalse(existe);
    }
}
