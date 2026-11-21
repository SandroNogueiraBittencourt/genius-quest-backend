package br.com.geniusquest.service;

import br.com.geniusquest.entity.TokenRecuperacaoSenha;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.TokenRecuperacaoInvalidoException;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;
import br.com.geniusquest.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private TokenRecuperacaoSenhaRepository tokens;
    @Mock private EmailService emails;
    @Mock private PasswordEncoder encoder;
    @Mock private RecuperacaoRateLimitService limiter;

    private RecuperacaoSenhaService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(usuarios, tokens, emails, encoder, limiter, "http://localhost:5173");
        lenient().when(limiter.permitir(anyString(), anyString())).thenReturn(true);
        lenient().when(limiter.permitirValidacao(anyString(), anyString())).thenReturn(true);
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Usuario Teste");
        usuario.setEmail("teste@example.com");
        usuario.setAtivo(true);
        usuario.setSenha("hash-antigo");
    }

    @Test
    void deveGerarTokenEEnviarLinkParaEmailCadastrado() throws Exception {
        when(usuarios.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
        Instant antes = Instant.now();

        service.solicitar(usuario.getEmail(), "127.0.0.1");

        var salvo = ArgumentCaptor.forClass(TokenRecuperacaoSenha.class);
        var codigoCaptor = ArgumentCaptor.forClass(String.class);
        var paginaCaptor = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(salvo.capture());
        verify(emails).enviarCodigoRecuperacao(eq(usuario.getEmail()), eq(usuario.getNome()),
                codigoCaptor.capture(), paginaCaptor.capture());
        assertEquals("http://localhost:5173/redefinir-senha", paginaCaptor.getValue());
        assertFalse(paginaCaptor.getValue().contains("token="));
        String token = codigoCaptor.getValue();
        assertTrue(token.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{16}"));
        assertSame(usuario, salvo.getValue().getUsuario());
        assertEquals(hash(token), salvo.getValue().getTokenHash());
        assertNotEquals(token, salvo.getValue().getTokenHash());
        assertFalse(salvo.getValue().getExpiraEm().isBefore(antes.plus(30, ChronoUnit.MINUTES)));
        assertFalse(salvo.getValue().getExpiraEm().isAfter(Instant.now().plus(30, ChronoUnit.MINUTES)));
    }

    @Test
    void naoDeveGerarTokenParaEmailInexistente() {
        when(usuarios.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.solicitar(usuario.getEmail(), "127.0.0.1"));
        verifyNoInteractions(tokens, emails, encoder);
    }

    @Test
    void naoDeveGerarTokenParaUsuarioInativo() {
        usuario.setAtivo(false);
        when(usuarios.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
        service.solicitar(usuario.getEmail(), "127.0.0.1");
        verifyNoInteractions(tokens, emails, encoder);
    }

    @Test
    void deveInvalidarTokensAnterioresAntesDeSalvarNovoToken() {
        when(usuarios.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
        service.solicitar(usuario.getEmail(), "127.0.0.1");
        var ordem = inOrder(tokens, emails);
        ordem.verify(tokens).invalidarTokensAbertos(usuario.getId());
        ordem.verify(tokens).save(any(TokenRecuperacaoSenha.class));
        ordem.verify(emails).enviarCodigoRecuperacao(eq(usuario.getEmail()), eq(usuario.getNome()), anyString(), anyString());
    }

    @Test
    void deveCodificarNovaSenhaEConsumirTokenValido() throws Exception {
        TokenRecuperacaoSenha token = token(false, Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(hash("ABCD2345EFGH6789"))).thenReturn(Optional.of(token));
        when(encoder.encode("NovaSenha123!")).thenReturn("hash-novo");

        service.redefinir("ABCD2345EFGH6789", "NovaSenha123!", "127.0.0.1");

        verify(encoder).encode("NovaSenha123!");
        assertEquals("hash-novo", usuario.getSenha());
        assertTrue(token.getUsado());
        assertEquals(1L, usuario.getVersaoCredenciais());
        verify(usuarios).save(usuario);
        verify(tokens).save(token);
        verifyNoInteractions(emails);
    }

    @Test
    void deveRejeitarTokenInexistenteSemAlterarSenha() throws Exception {
        when(tokens.findByTokenHash(hash("ABCD2345EFGH6789"))).thenReturn(Optional.empty());
        assertThrows(TokenRecuperacaoInvalidoException.class,
                () -> service.redefinir("ABCD2345EFGH6789", "NovaSenha123!", "127.0.0.1"));
        verificarSenhaPreservada();
    }

    @Test
    void deveRejeitarTokenExpiradoSemAlterarSenha() throws Exception {
        TokenRecuperacaoSenha token = token(false, Instant.now().minusSeconds(60));
        when(tokens.findByTokenHash(hash("ABCD2345EFGH6789"))).thenReturn(Optional.of(token));
        assertThrows(TokenRecuperacaoInvalidoException.class,
                () -> service.redefinir("ABCD2345EFGH6789", "NovaSenha123!", "127.0.0.1"));
        assertFalse(token.getUsado());
        verificarSenhaPreservada();
    }

    @Test
    void deveRejeitarTokenJaUsadoSemAlterarSenha() throws Exception {
        TokenRecuperacaoSenha token = token(true, Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(hash("ABCD2345EFGH6789"))).thenReturn(Optional.of(token));
        assertThrows(TokenRecuperacaoInvalidoException.class,
                () -> service.redefinir("ABCD2345EFGH6789", "NovaSenha123!", "127.0.0.1"));
        assertTrue(token.getUsado());
        verificarSenhaPreservada();
    }


    @Test
    void naoCriaTokenQuandoRateLimitBloqueia() {
        when(limiter.permitir(usuario.getEmail(), "127.0.0.1")).thenReturn(false);
        service.solicitar(usuario.getEmail(), "127.0.0.1");
        verifyNoInteractions(usuarios, tokens, emails);
    }

    @Test
    void soEnviaEmailAposCommitDaTransacao() {
        when(usuarios.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.solicitar(usuario.getEmail(), "127.0.0.1");
            verifyNoInteractions(emails);
            var sincronizacoes = org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations();
            assertEquals(1, sincronizacoes.size());
            sincronizacoes.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(emails).enviarCodigoRecuperacao(eq(usuario.getEmail()), eq(usuario.getNome()), anyString(), anyString());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void deveBloquearTentativasDeValidacaoExcessivas() {
        when(limiter.permitirValidacao("ABCD2345EFGH6789", "127.0.0.1"))
                .thenReturn(false);
        assertThrows(TokenRecuperacaoInvalidoException.class,
                () -> service.redefinir("ABCD2345EFGH6789", "NovaSenha123!", "127.0.0.1"));
        verifyNoInteractions(tokens, usuarios, emails, encoder);
    }

    @Test
    void aceitaCodigoComHifensSemAlterarHashPersistido() throws Exception {
        TokenRecuperacaoSenha codigo = token(false, Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(hash("ABCD2345EFGH6789"))).thenReturn(Optional.of(codigo));
        when(encoder.encode("NovaSenha123!")).thenReturn("nova-senha-hash");
        service.redefinir("ABCD-2345-EFGH-6789", "NovaSenha123!", "127.0.0.1");
        assertTrue(codigo.getUsado());
    }

    private void verificarSenhaPreservada() {
        assertEquals("hash-antigo", usuario.getSenha());
        verifyNoInteractions(usuarios, encoder, emails);
        verify(tokens, never()).save(any());
    }

    private TokenRecuperacaoSenha token(boolean usado, Instant expiraEm) {
        var token = new TokenRecuperacaoSenha();
        token.setUsuario(usuario);
        token.setUsado(usado);
        token.setExpiraEm(expiraEm);
        return token;
    }

    private String hash(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8)));
    }
}
