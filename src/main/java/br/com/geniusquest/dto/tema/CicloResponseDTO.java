package br.com.geniusquest.dto.tema;

import br.com.geniusquest.entity.StatusCicloVotacao;
import java.time.Instant;
import java.util.UUID;

public record CicloResponseDTO(
        UUID id, String nome, Instant inicioEm, Instant fimEm,
        StatusCicloVotacao status, UUID vencedorTemaId, Instant encerradoEm
) {}
