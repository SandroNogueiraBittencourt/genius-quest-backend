package br.com.geniusquest.config;

import br.com.geniusquest.repository.TokenRevogadoRepository;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Remove registros de tokens ja expirados diariamente. */
@Configuration
@EnableScheduling
public class TokenRevocationCleanup {

    private final TokenRevogadoRepository repository;

    public TokenRevocationCleanup(TokenRevogadoRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${app.jwt.revocation-cleanup-cron:0 0 3 * * *}", zone = "UTC")
    public void limparExpirados() {
        repository.removerExpirados(Instant.now());
    }
}
