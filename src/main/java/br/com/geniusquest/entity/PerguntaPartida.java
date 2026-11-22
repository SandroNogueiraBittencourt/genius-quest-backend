package br.com.geniusquest.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot imutável da questão quando uma partida começa; gabarito exclusivamente interno. */
@Entity
@Table(name = "perguntas_partida")
public class PerguntaPartida {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partida_id", nullable = false, updatable = false)
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pergunta_id", nullable = false, updatable = false)
    private Pergunta pergunta;

    @Column(nullable = false, updatable = false)
    private int ordem;

    @Column(nullable = false, length = 500, updatable = false)
    private String enunciado;

    @Column(name = "alternativa_0", nullable = false, length = 250, updatable = false)
    private String alternativa0;
    @Column(name = "alternativa_1", nullable = false, length = 250, updatable = false)
    private String alternativa1;
    @Column(name = "alternativa_2", nullable = false, length = 250, updatable = false)
    private String alternativa2;
    @Column(name = "alternativa_3", nullable = false, length = 250, updatable = false)
    private String alternativa3;

    @Column(name = "resposta_correta_indice", nullable = false, updatable = false)
    private int respostaCorretaIndice;

    @Enumerated(EnumType.STRING)
    @Column(name = "dificuldade_inicial", nullable = false, length = 10, updatable = false)
    private DificuldadePergunta dificuldadeInicial;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (criadoEm == null) criadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public Partida getPartida() { return partida; }
    public void setPartida(Partida partida) { this.partida = partida; }
    public Pergunta getPergunta() { return pergunta; }
    public void setPergunta(Pergunta pergunta) { this.pergunta = pergunta; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }
    public List<String> getAlternativas() {
        return List.of(alternativa0, alternativa1, alternativa2, alternativa3);
    }
    public void setAlternativas(List<String> alternativas) {
        if (alternativas == null || alternativas.size() != 4) {
            throw new IllegalArgumentException("São necessárias quatro alternativas");
        }
        alternativa0 = alternativas.get(0);
        alternativa1 = alternativas.get(1);
        alternativa2 = alternativas.get(2);
        alternativa3 = alternativas.get(3);
    }
    public int getRespostaCorretaIndice() { return respostaCorretaIndice; }
    public void setRespostaCorretaIndice(int respostaCorretaIndice) {
        this.respostaCorretaIndice = respostaCorretaIndice;
    }
    public DificuldadePergunta getDificuldadeInicial() { return dificuldadeInicial; }
    public void setDificuldadeInicial(DificuldadePergunta dificuldadeInicial) {
        this.dificuldadeInicial = dificuldadeInicial;
    }
    public Instant getCriadoEm() { return criadoEm; }
}
