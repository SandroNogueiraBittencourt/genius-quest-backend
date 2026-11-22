package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Resposta do criador à questão congelada. A correção nunca é calculada no cliente. */
@Entity
@Table(name = "respostas_partida")
public class RespostaPartida {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pergunta_partida_id", nullable = false, updatable = false)
    private PerguntaPartida perguntaPartida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "resposta_indice", nullable = false, updatable = false)
    private int respostaIndice;

    @Column(nullable = false, updatable = false)
    private boolean correta;

    @Column(name = "respondida_em", nullable = false, updatable = false)
    private Instant respondidaEm;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (respondidaEm == null) respondidaEm = Instant.now();
    }

    public UUID getId() { return id; }
    public PerguntaPartida getPerguntaPartida() { return perguntaPartida; }
    public void setPerguntaPartida(PerguntaPartida perguntaPartida) { this.perguntaPartida = perguntaPartida; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public int getRespostaIndice() { return respostaIndice; }
    public void setRespostaIndice(int respostaIndice) { this.respostaIndice = respostaIndice; }
    public boolean isCorreta() { return correta; }
    public void setCorreta(boolean correta) { this.correta = correta; }
    public Instant getRespondidaEm() { return respondidaEm; }
}
