package br.com.geniusquest.dto.pergunta;

/** Estado de configuracao e cotas; disponivel somente a ADMIN. */
public record GeracaoIAStatusDTO(
        boolean habilitada,
        int maximoPorSolicitacao,
        int limiteRequisicoesDiarias,
        int requisicoesHoje,
        int limitePerguntasDiarias,
        int perguntasHoje) {
}
