package br.com.geniusquest.dto.tema;

import java.time.Instant;
import java.util.UUID;

public record CicloHistoricoDTO(
        UUID id, String nome, Instant inicioEm, Instant fimEm,
        Instant encerradoEm, UUID vencedorTemaId, String vencedorTitulo,
        long totalVotos, boolean rankingReconstruido
) {}
