package br.com.geniusquest.mapper;

import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsuarioMapperTest {
    private final UsuarioMapper mapper = new UsuarioMapper();

    @Test
    void respostaPublicaNaoSerializaSenhaNemVersaoDeCredenciais() {
        var usuario = new Usuario();
        usuario.setNome("Pessoa Teste");
        usuario.setEmail("teste@exemplo.test");
        usuario.setSenha("hash-apenas-teste");
        usuario.incrementarVersaoCredenciais();
        usuario.prePersist();

        var json = JsonMapper.builder().build().valueToTree(mapper.toResponse(usuario));

        assertEquals("teste@exemplo.test", json.get("email").asText());
        assertFalse(json.has("senha"));
        assertFalse(json.has("versaoCredenciais"));
    }

    @Test
    void copiaNomeEEmailSemGuardarSenhaEmTextoPuro() {
        var cadastro = new UsuarioCadastroDTO("Pessoa Teste", "teste@exemplo.test", "SenhaTeste123!");

        var usuario = mapper.toEntity(cadastro);

        assertEquals(cadastro.nome(), usuario.getNome());
        assertEquals(cadastro.email(), usuario.getEmail());
        assertNull(usuario.getSenha());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservaEstadoDeVerificacaoNaResposta(boolean verificado) {
        var usuario = new Usuario();
        usuario.setNome("Pessoa Teste");
        usuario.setEmail("teste@exemplo.test");
        usuario.setPerfil(PerfilUsuario.ADMIN);
        usuario.setMoedas(12);
        usuario.setAtivo(false);
        usuario.setEmailVerificado(verificado);
        usuario.prePersist();

        assertEquals(new UsuarioResponseDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                PerfilUsuario.ADMIN, 12, false, usuario.getCriadoEm(), usuario.getAtualizadoEm(), verificado),
                mapper.toResponse(usuario));
    }
}
