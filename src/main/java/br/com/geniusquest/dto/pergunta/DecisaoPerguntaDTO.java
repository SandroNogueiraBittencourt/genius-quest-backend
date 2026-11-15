package br.com.geniusquest.dto.pergunta;

import br.com.geniusquest.entity.AcaoCuradoriaPergunta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoPerguntaDTO(
        @NotNull AcaoCuradoriaPergunta acao,
        @Size(max = 500) String motivo) {
}
