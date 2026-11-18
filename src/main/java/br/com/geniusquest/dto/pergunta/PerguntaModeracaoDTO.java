package br.com.geniusquest.dto.pergunta;

import br.com.geniusquest.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ATENÇÃO: contém gabarito. Não reutilizar em APIs acessíveis aos jogadores. */
public record PerguntaModeracaoDTO(
        UUID id, UUID temaId, String enunciado, List<String> alternativas,
        int respostaCorretaIndice, StatusPergunta status, OrigemPergunta origem,
        DificuldadePergunta dificuldadeInicial, String fonteReferencia, String modeloIa,
        UUID revisorId, String motivoRevisao, Instant revisadoEm, Instant criadoEm) {
}
