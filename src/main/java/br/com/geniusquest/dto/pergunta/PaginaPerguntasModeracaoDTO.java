package br.com.geniusquest.dto.pergunta;

import java.util.List;

public record PaginaPerguntasModeracaoDTO(List<PerguntaModeracaoDTO> content, PageInfo page) {
    public record PageInfo(int size, int number, long totalElements, int totalPages) { }
}
