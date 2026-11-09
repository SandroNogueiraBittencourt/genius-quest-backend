package br.com.geniusquest.dto.tema;

import java.util.List;

public record HistoricoVotacoesDTO(
        List<CicloHistoricoDTO> ciclos, int pagina, int tamanho,
        long total, boolean temProxima
) {}
