package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ciclos_votacao")
public class CicloVotacao {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "inicio_em", nullable = false)
    private Instant inicioEm;

    @Column(name = "fim_em", nullable = false)
    private Instant fimEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCicloVotacao status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vencedor_tema_id")
    private Tema vencedor;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = StatusCicloVotacao.ABERTO;
        criadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Instant getInicioEm() { return inicioEm; }
    public void setInicioEm(Instant inicioEm) { this.inicioEm = inicioEm; }
    public Instant getFimEm() { return fimEm; }
    public void setFimEm(Instant fimEm) { this.fimEm = fimEm; }
    public StatusCicloVotacao getStatus() { return status; }
    public void setStatus(StatusCicloVotacao status) { this.status = status; }
    public Tema getVencedor() { return vencedor; }
    public void setVencedor(Tema vencedor) { this.vencedor = vencedor; }
    public Instant getEncerradoEm() { return encerradoEm; }
    public void setEncerradoEm(Instant encerradoEm) { this.encerradoEm = encerradoEm; }
    public Instant getCriadoEm() { return criadoEm; }
}
