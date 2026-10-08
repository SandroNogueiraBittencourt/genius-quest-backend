package br.com.geniusquest.dto.erro;

import java.time.Instant;
import java.util.Map;

public record ErroRespostaDTO(

        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        String path,
        Map<String, String> campos

) {
}
