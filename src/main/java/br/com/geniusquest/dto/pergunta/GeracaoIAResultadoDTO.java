package br.com.geniusquest.dto.pergunta;

import java.util.List;
import java.util.UUID;

/** IDs da fila PENDENTE; nao contem alternativas nem gabaritos. */
public record GeracaoIAResultadoDTO(
        List<UUID> ids,
        String status) {
}
