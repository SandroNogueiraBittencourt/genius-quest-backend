package br.com.geniusquest.dto.pergunta;

import br.com.geniusquest.entity.AcaoCuradoriaPergunta;
import br.com.geniusquest.entity.StatusPergunta;
import java.time.Instant;
import java.util.UUID;

/** Snapshots podem conter gabarito: disponível apenas para ADMIN. */
public record HistoricoCuradoriaDTO(
        UUID id, UUID revisorId, AcaoCuradoriaPergunta acao,
        StatusPergunta statusAnterior, StatusPergunta statusNovo,
        String motivo, String antes, String depois, Instant criadoEm) {
}
