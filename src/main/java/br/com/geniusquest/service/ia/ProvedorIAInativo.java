package br.com.geniusquest.service.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Comportamento seguro: nenhuma chamada paga é feita por padrão. */
@Component
@ConditionalOnProperty(prefix = "app.ia", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ProvedorIAInativo implements ProvedorPerguntasIA {
    @Override
    public LoteGeradoIA gerar(ContextoGeracaoIA contexto, int quantidade) {
        throw new IllegalStateException("Geração por IA desativada no servidor.");
    }
}
