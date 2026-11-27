package br.com.geniusquest.validation;

import br.com.geniusquest.dto.recuperacao_senha.RedefinirSenhaRequest;
import br.com.geniusquest.dto.usuario.AlterarSenhaRequest;
import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SenhaForteTest {
    @ParameterizedTest
    @ValueSource(strings = {"senhaforte1!", "SENHAFORTE1!", "SenhaForte!", "SenhaForte1 "})
    void senhasNovasExigemTodosOsTiposDeCaractere(String senha) {
        verificar(senha, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SenhaForte1!", "Árvore9€"})
    void fluxosDeSenhaNovaAceitamSenhaForte(String senha) {
        verificar(senha, 0);
    }

    @Test
    void rejeitaSenhaQueUltrapassaLimiteDoBCryptEmBytes() {
        verificar("Á".repeat(35) + "a1!", 1);
    }

    @Test
    void aceitaSenhaQueTemExatamente72BytesEmUtf8() {
        verificar("Á".repeat(34) + "aa1!", 0);
    }

    @Test
    void senhaAtualPodeSerLegadaMasDeveSerInformada() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validador = factory.getValidator();
            assertEquals(0, validador.validate(new AlterarSenhaRequest("antiga", "NovaSenha123!")).size());
            var erros = validador.validate(new AlterarSenhaRequest("  ", "NovaSenha123!"));
            assertEquals(1, erros.size());
            assertEquals("senhaAtual", erros.iterator().next().getPropertyPath().toString());
        }
    }

    private void verificar(String senha, int errosEsperados) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validador = factory.getValidator();
            assertEquals(errosEsperados, validador.validateValue(UsuarioCadastroDTO.class, "senha", senha).size());
            assertEquals(errosEsperados, validador.validateValue(RedefinirSenhaRequest.class, "novaSenha", senha).size());
            assertEquals(errosEsperados, validador.validateValue(AlterarSenhaRequest.class, "novaSenha", senha).size());
        }
    }
}
