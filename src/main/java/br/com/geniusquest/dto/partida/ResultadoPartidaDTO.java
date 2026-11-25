package br.com.geniusquest.dto.partida;

import java.util.List;
import java.util.UUID;

public record ResultadoPartidaDTO(UUID partidaId, List<Jogador> classificacao) {
    public record Jogador(UUID id, String nome, int posicao, int pontos, int acertos, int erros) {}
}
