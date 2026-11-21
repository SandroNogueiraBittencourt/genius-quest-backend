package br.com.geniusquest.service;

import br.com.geniusquest.dto.pergunta.*;
import br.com.geniusquest.exception.RegraTemasException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Dupla habilitacao e reservas persistentes antes de qualquer chamada paga. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class GeracaoIAGatewayService {
    private final GeracaoPerguntasIAService geracao;
    private final GeracaoIAQuotaService cota;
    private final PerguntasPersistenciaService persistencia;
    private final boolean iaHabilitada;
    private final boolean endpointHabilitado;
    private final boolean chaveConfigurada;

    public GeracaoIAGatewayService(GeracaoPerguntasIAService geracao,
            GeracaoIAQuotaService cota,
            PerguntasPersistenciaService persistencia,
            @Value("${app.ia.enabled:false}") boolean iaHabilitada,
            @Value("${app.ia.http-enabled:false}") boolean endpointHabilitado,
            @Value("${OPENAI_API_KEY:}") String chaveOpenAi) {
        this.geracao = geracao;
        this.cota = cota;
        this.persistencia = persistencia;
        this.iaHabilitada = iaHabilitada;
        this.endpointHabilitado = endpointHabilitado;
        // Nao reter, exibir nem registrar a chave. Evita cota debitada sem API configurada.
        this.chaveConfigurada = chaveOpenAi != null && !chaveOpenAi.isBlank();
    }

    public GeracaoIAStatusDTO estado() {
        var uso = cota.usoHoje();
        return new GeracaoIAStatusDTO(
                iaHabilitada && endpointHabilitado && chaveConfigurada,
                GeracaoIAQuotaService.MAX_POR_SOLICITACAO,
                GeracaoIAQuotaService.MAX_REQUISICOES_DIA,
                uso.requisicoes(),
                GeracaoIAQuotaService.MAX_PERGUNTAS_DIA,
                uso.perguntas());
    }

    /** Sempre retorna apenas os IDs PENDENTES, nunca aprova perguntas. */
    public GeracaoIAResultadoDTO gerar(PedidoGeracaoIADTO pedido, UUID chave,
            String solicitante) {
        if (!iaHabilitada || !endpointHabilitado || !chaveConfigurada) {
            throw new RegraTemasException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Geracao por IA desabilitada no servidor.");
        }
        if (pedido == null || pedido.temaId() == null
                || pedido.quantidade() == null
                || pedido.quantidade() < 1
                || pedido.quantidade() > GeracaoIAQuotaService.MAX_POR_SOLICITACAO) {
            throw new RegraTemasException(HttpStatus.BAD_REQUEST,
                    "Solicitacao IA invalida.");
        }

        // Erros de tema inexistente ou nao aprovado nao devem gastar cota.
        // O servico de geracao revalida o tema depois da chamada externa.
        persistencia.contexto(pedido.temaId());

        // Esta transacao termina antes do HTTP e contabiliza ate falhas/timeout.
        cota.reservar(chave, solicitante, pedido.temaId(), pedido.quantidade());
        try {
            List<UUID> ids = geracao.gerarCandidatas(pedido.temaId(), pedido.quantidade());
            cota.concluir(chave);
            return new GeracaoIAResultadoDTO(ids, "PENDENTE");
        } catch (RuntimeException ex) {
            cota.falhar(chave);
            // Nunca enviar payload de provedor, prompt, tokens ou resposta de erro bruta.
            throw new RegraTemasException(HttpStatus.BAD_GATEWAY,
                    "A geracao falhou ou nao foi confirmada. Verifique a fila antes de tentar novamente.");
        }
    }
}
