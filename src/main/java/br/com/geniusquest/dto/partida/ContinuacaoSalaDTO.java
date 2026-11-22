package br.com.geniusquest.dto.partida;

public record ContinuacaoSalaDTO(Status status, Boolean meuVoto, int favoraveis, int contrarios, int totalJogadores) {
    public enum Status { AGUARDANDO, APROVADA, ENCERRADA }
}
