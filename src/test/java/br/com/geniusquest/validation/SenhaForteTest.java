package br.com.geniusquest.validation;

import br.com.geniusquest.dto.recuperacao_senha.RedefinirSenhaRequest;
import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SenhaForteTest {
    @ParameterizedTest
    @ValueSource(strings = {"senhaforte1!", "SENHAFORTE1!", "SenhaForte!", "SenhaForte1 "})
    void cadastroERedefinicaoExigemTodosOsTiposDeCaractere(String senha) {
        verificar(senha, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SenhaForte1!", "Árvore9€"})
    void cadastroERedefinicaoAceitamSenhaForte(String senha) {
        verificar(senha, 0);
    }

    @org.junit.jupiter.api.Test
    void rejeitaSenhaQueUltrapassaLimiteDoBCryptEmBytes() {
        verificar("Á".repeat(35) + "a1!", 1);
    }

    private void verificar(String senha, int errosEsperados) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validador = factory.getValidator();
            assertEquals(errosEsperados, validador.validateValue(UsuarioCadastroDTO.class, "senha", senha).size());
            assertEquals(errosEsperados, validador.validateValue(RedefinirSenhaRequest.class, "novaSenha", senha).size());
        }
    }
}
