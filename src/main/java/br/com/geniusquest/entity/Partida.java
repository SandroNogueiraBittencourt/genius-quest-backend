package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partidas")
public class Partida {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ciclo_id", nullable = false, updatable = false)
    private CicloVotacao ciclo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tema_id", nullable = false, updatable = false)
    private Tema tema;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criador_id", nullable = false, updatable = false)
    private Usuario criador;

    @Column(name = "tema_titulo", nullable = false, length = 120, updatable = false)
    private String temaTitulo;

    @Column(name = "categoria_nome", nullable = false, length = 80, updatable = false)
    private String categoriaNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StatusPartida status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = StatusPartida.PREPARACAO;
        if (criadoEm == null) criadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public CicloVotacao getCiclo() { return ciclo; }
    public void setCiclo(CicloVotacao ciclo) { this.ciclo = ciclo; }
    public Tema getTema() { return tema; }
    public void setTema(Tema tema) { this.tema = tema; }
    public Usuario getCriador() { return criador; }
    public void setCriador(Usuario criador) { this.criador = criador; }
    public String getTemaTitulo() { return temaTitulo; }
    public void setTemaTitulo(String temaTitulo) { this.temaTitulo = temaTitulo; }
    public String getCategoriaNome() { return categoriaNome; }
    public void setCategoriaNome(String categoriaNome) { this.categoriaNome = categoriaNome; }
    public StatusPartida getStatus() { return status; }
    public Instant getCriadoEm() { return criadoEm; }
}
