package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "resultados_ciclo_votacao")
public class ResultadoTemaCiclo {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ciclo_id", nullable = false)
    private UUID cicloId;

    @Column(name = "tema_id", nullable = false)
    private UUID temaId;

    @Column(nullable = false)
    private int posicao;

    @Column(name = "titulo_tema", nullable = false, length = 120)
    private String tituloTema;

    @Column(nullable = false)
    private long votos;

    @Column(name = "votantes_distintos", nullable = false)
    private long votantesDistintos;

    @Column(nullable = false)
    private boolean reconstruido;

    protected ResultadoTemaCiclo() {}

    public ResultadoTemaCiclo(UUID cicloId, UUID temaId, int posicao, String tituloTema,
            long votos, long votantesDistintos, boolean reconstruido) {
        this.cicloId = cicloId;
        this.temaId = temaId;
        this.posicao = posicao;
        this.tituloTema = tituloTema;
        this.votos = votos;
        this.votantesDistintos = votantesDistintos;
        this.reconstruido = reconstruido;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public UUID getCicloId() { return cicloId; }
    public UUID getTemaId() { return temaId; }
    public int getPosicao() { return posicao; }
    public String getTituloTema() { return tituloTema; }
    public long getVotos() { return votos; }
    public long getVotantesDistintos() { return votantesDistintos; }
    public boolean isReconstruido() { return reconstruido; }
}
