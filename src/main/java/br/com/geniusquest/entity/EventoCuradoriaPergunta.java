package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Registro append-only: serviço não oferece operações de alteração ou exclusão. */
@Entity
@Table(name = "eventos_curadoria_pergunta")
public class EventoCuradoriaPergunta {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pergunta_id", nullable = false, updatable = false)
    private Pergunta pergunta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revisor_id", nullable = false, updatable = false)
    private Usuario revisor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AcaoCuradoriaPergunta acao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", nullable = false, updatable = false, length = 20)
    private StatusPergunta statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, updatable = false, length = 20)
    private StatusPergunta statusNovo;

    @Column(length = 500, updatable = false)
    private String motivo;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String antes;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String depois;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (criadoEm == null) criadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public Pergunta getPergunta() { return pergunta; }
    public void setPergunta(Pergunta pergunta) { this.pergunta = pergunta; }
    public Usuario getRevisor() { return revisor; }
    public void setRevisor(Usuario revisor) { this.revisor = revisor; }
    public AcaoCuradoriaPergunta getAcao() { return acao; }
    public void setAcao(AcaoCuradoriaPergunta acao) { this.acao = acao; }
    public StatusPergunta getStatusAnterior() { return statusAnterior; }
    public void setStatusAnterior(StatusPergunta statusAnterior) { this.statusAnterior = statusAnterior; }
    public StatusPergunta getStatusNovo() { return statusNovo; }
    public void setStatusNovo(StatusPergunta statusNovo) { this.statusNovo = statusNovo; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getAntes() { return antes; }
    public void setAntes(String antes) { this.antes = antes; }
    public String getDepois() { return depois; }
    public void setDepois(String depois) { this.depois = depois; }
    public Instant getCriadoEm() { return criadoEm; }
}
