package br.com.geniusquest.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.geniusquest.entity.TokenRecuperacaoSenha;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.TokenRecuperacaoInvalidoException;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;
import br.com.geniusquest.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RecuperacaoSenhaService {
    private final CodigoRecuperacao codigos;

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacaoRateLimitService rateLimitService;
    private final String frontendUrl;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository,
                                   TokenRecuperacaoSenhaRepository tokenRepository,
                                   EmailService emailService,
                                   PasswordEncoder passwordEncoder,
                                   RecuperacaoRateLimitService rateLimitService,
                                   CodigoRecuperacao codigos,
                                   @Value("${app.frontend-url}") String frontendUrl) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.frontendUrl = frontendUrl;
        this.codigos = codigos;
    }

    @Transactional
    public void solicitar(String email, String origem) {
        // Mantem a mesma resposta para e-mail existente, inexistente ou limitado.
        if (!rateLimitService.permitir(email, origem)) {
            return;
        }
        // Nunca revela se o e-mail existe: o controller responde igual nos dois casos
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::getAtivo)
                .ifPresent(usuario -> {
                    tokenRepository.invalidarTokensAbertos(usuario.getId());

                    String codigo = CodigoRecuperacao.gerar();

                    TokenRecuperacaoSenha t = new TokenRecuperacaoSenha();
                    t.setUsuario(usuario);
                    t.setTokenHash(codigos.hash("recuperacao", codigo));
                    t.setExpiraEm(Instant.now().plus(CodigoRecuperacao.VALIDADE_MINUTOS, ChronoUnit.MINUTES));
                    tokenRepository.save(t);

                    String pagina = frontendUrl.replaceAll("/+$", "") + "/redefinir-senha";
                    String destino = usuario.getEmail();
                    String nome = usuario.getNome();
                    // Evita enviar links de tokens que nao foram confirmados no banco.
                    if (TransactionSynchronizationManager.isActualTransactionActive()
                            && TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(
                                new TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        emailService.enviarCodigoRecuperacao(destino, nome, codigo, pagina);
                                    }
                                });
                    } else {
                        // Somente para invocacoes de testes unitarios sem transacao.
                        emailService.enviarCodigoRecuperacao(destino, nome, codigo, pagina);
                    }
                });
    }

    @Transactional
    public void redefinir(String codigo, String novaSenha, String origem) {
        // Limite transacional antes de consultar codigo, mesmo quando ele e invalido.
        if (!rateLimitService.permitirValidacao(codigo, origem)
                || !CodigoRecuperacao.formatoValido(codigo)) {
            throw new TokenRecuperacaoInvalidoException();
        }
        String normalizado = CodigoRecuperacao.normalizar(codigo);
        TokenRecuperacaoSenha t = tokenRepository.findByTokenHash(codigos.hash("recuperacao", normalizado))
                .filter(x -> !x.getUsado() && !x.expirado())
                .orElseThrow(TokenRecuperacaoInvalidoException::new);

        Usuario usuario = t.getUsuario();
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.incrementarVersaoCredenciais();
        usuarioRepository.save(usuario);

        t.setUsado(true);
        tokenRepository.save(t);
        // Invalida tambem outros links de recuperacao eventualmente abertos.
        tokenRepository.invalidarTokensAbertos(usuario.getId());
    }

}
