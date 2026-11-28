package br.com.geniusquest.service;

import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.EmailJaCadastradoException;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.UsuarioRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class VerificacaoEmailService {
    private final UsuarioRepository usuarios;
    private final TokenRecuperacaoSenhaRepository recuperacao;
    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final CodigoVerificacaoEmail codigos;
    private final RecuperacaoRateLimitService limites;

    public VerificacaoEmailService(UsuarioRepository usuarios, JdbcTemplate jdbc, EmailService email,
            RecuperacaoRateLimitService limites, CodigoVerificacaoEmail codigos, TokenRecuperacaoSenhaRepository recuperacao) {
        this.usuarios = usuarios;
        this.recuperacao = recuperacao;
        this.jdbc = jdbc;
        this.email = email;
        this.limites = limites;
        this.codigos = codigos;
    }

    void emitir(Usuario usuario) {
        emitir(usuario, null);
    }

    void limitarSolicitacaoTroca(Usuario usuario) {
        if (!limites.permitirSolicitacaoTroca(usuario.getId())) {
            throw new RegraTemasException(HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde antes de solicitar outra troca de e-mail.");
        }
    }

    void emitirTroca(Usuario usuario, String novoEmail) {
        emitir(usuario, novoEmail);
    }

    private void emitir(Usuario usuario, String novoEmail) {
        String codigo = CodigoVerificacaoEmail.gerar();
        jdbc.update("""
                INSERT INTO codigos_verificacao_email (usuario_id, codigo_hash, expira_em, novo_email) VALUES (?, ?, ?, ?)
                ON CONFLICT (usuario_id) DO UPDATE SET codigo_hash = EXCLUDED.codigo_hash,
                    expira_em = EXCLUDED.expira_em, novo_email = EXCLUDED.novo_email
                """, usuario.getId(), codigos.hash("verificacao", codigo),
                Timestamp.from(Instant.now().plus(CodigoVerificacaoEmail.VALIDADE_MINUTOS, ChronoUnit.MINUTES)), novoEmail);

        String destino = novoEmail == null ? usuario.getEmail() : novoEmail;
        String nome = usuario.getNome();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                email.enviarCodigoVerificacao(destino, nome, codigo);
            }
        });
    }

    @Transactional
    public void reenviar(String endereco, String origem) {
        if (!limites.permitirEnvioVerificacao(endereco, origem)) return;
        usuarios.bloquearPorEmail(endereco.strip())
                .filter(usuario -> Boolean.TRUE.equals(usuario.getAtivo()) && !usuario.isEmailVerificado())
                .ifPresent(this::emitir);
    }

    @Transactional
    public void verificar(String endereco, String codigo, String origem) {
        if (!limites.permitirValidacaoVerificacao(endereco, origem) || !CodigoVerificacaoEmail.formatoValido(codigo)) {
            throw codigoInvalido();
        }
        Usuario usuario = usuarios.bloquearPorEmail(endereco.strip())
                .filter(conta -> Boolean.TRUE.equals(conta.getAtivo()) && !conta.isEmailVerificado())
                .orElseThrow(this::codigoInvalido);
        int consumidos = jdbc.update("""
                DELETE FROM codigos_verificacao_email
                WHERE usuario_id = ? AND codigo_hash = ? AND expira_em > CURRENT_TIMESTAMP AND novo_email IS NULL
                """, usuario.getId(), codigos.hash("verificacao", CodigoVerificacaoEmail.normalizar(codigo)));
        if (consumidos != 1) throw codigoInvalido();
        usuario.setEmailVerificado(true);
    }

    @Transactional
    public void confirmarTroca(String emailAtual, String codigo, String origem) {
        if (!limites.permitirValidacaoVerificacao(emailAtual, origem)
                || !CodigoVerificacaoEmail.formatoValido(codigo)) {
            throw codigoInvalido();
        }
        Usuario usuario = usuarios.bloquearPorEmail(emailAtual)
                .filter(conta -> Boolean.TRUE.equals(conta.getAtivo()) && conta.isEmailVerificado())
                .orElseThrow(this::codigoInvalido);
        String novoEmail = jdbc.query("""
                DELETE FROM codigos_verificacao_email
                WHERE usuario_id = ? AND codigo_hash = ? AND expira_em > CURRENT_TIMESTAMP AND novo_email IS NOT NULL
                RETURNING novo_email
                """, rs -> rs.next() ? rs.getString(1) : null,
                usuario.getId(), codigos.hash("verificacao", codigo));
        if (novoEmail == null) throw codigoInvalido();
        if (usuarios.existsByEmailIgnoreCase(novoEmail)) throw new EmailJaCadastradoException();

        recuperacao.invalidarTokensAbertos(usuario.getId());
        usuario.setEmail(novoEmail);
        usuario.incrementarVersaoCredenciais();
        try {
            usuarios.flush();
        } catch (DataIntegrityViolationException erro) {
            if (erro.getCause() instanceof ConstraintViolationException restricao
                    && "usuarios_email_key".equals(restricao.getConstraintName())) {
                throw new EmailJaCadastradoException();
            }
            throw erro;
        }
    }

    private RegraTemasException codigoInvalido() {
        return new RegraTemasException(HttpStatus.BAD_REQUEST, "Código inválido, expirado ou já utilizado. Confira o código ou solicite outro.");
    }
}
