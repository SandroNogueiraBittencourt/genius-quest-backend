package br.com.geniusquest.service;

import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.UsuarioRepository;
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
    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final CodigoRecuperacao codigos;
    private final RecuperacaoRateLimitService limites;

    public VerificacaoEmailService(UsuarioRepository usuarios, JdbcTemplate jdbc, EmailService email,
            RecuperacaoRateLimitService limites, CodigoRecuperacao codigos) {
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.email = email;
        this.limites = limites;
        this.codigos = codigos;
    }

    void emitir(Usuario usuario) {
        String codigo = CodigoRecuperacao.gerar();
        jdbc.update("""
                INSERT INTO codigos_verificacao_email (usuario_id, codigo_hash, expira_em) VALUES (?, ?, ?)
                ON CONFLICT (usuario_id) DO UPDATE SET codigo_hash = EXCLUDED.codigo_hash,
                    expira_em = EXCLUDED.expira_em
                """, usuario.getId(), codigos.hash("verificacao", codigo),
                Timestamp.from(Instant.now().plus(CodigoRecuperacao.VALIDADE_MINUTOS, ChronoUnit.MINUTES)));

        String destino = usuario.getEmail();
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
        if (!limites.permitirValidacaoVerificacao(endereco, origem) || !CodigoRecuperacao.formatoValido(codigo)) {
            throw codigoInvalido();
        }
        Usuario usuario = usuarios.bloquearPorEmail(endereco.strip())
                .filter(conta -> Boolean.TRUE.equals(conta.getAtivo()) && !conta.isEmailVerificado())
                .orElseThrow(this::codigoInvalido);
        int consumidos = jdbc.update("""
                DELETE FROM codigos_verificacao_email
                WHERE usuario_id = ? AND codigo_hash = ? AND expira_em > CURRENT_TIMESTAMP
                """, usuario.getId(), codigos.hash("verificacao", CodigoRecuperacao.normalizar(codigo)));
        if (consumidos != 1) throw codigoInvalido();
        usuario.setEmailVerificado(true);
    }

    private RegraTemasException codigoInvalido() {
        return new RegraTemasException(HttpStatus.BAD_REQUEST, "Código inválido, expirado ou já utilizado. Confira o código ou solicite outro.");
    }
}
