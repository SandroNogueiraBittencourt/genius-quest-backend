package br.com.geniusquest.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "perguntas")
public class Pergunta {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tema_id", nullable = false)
    private Tema tema;

    @Column(nullable = false, length = 500)
    private String enunciado;

    @Column(name = "enunciado_normalizado", nullable = false, length = 500)
    private String enunciadoNormalizado;

    @Column(name = "resposta_correta_indice", nullable = false)
    private int respostaCorretaIndice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPergunta status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemPergunta origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "dificuldade_inicial", nullable = false, length = 10)
    private DificuldadePergunta dificuldadeInicial;

    @Column(name = "fonte_referencia", length = 500)
    private String fonteReferencia;

    @Column(name = "modelo_ia", length = 100)
    private String modeloIa;

    @Column(name = "versao_geracao", length = 60)
    private String versaoGeracao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisor_id")
    private Usuario revisor;

    @Column(name = "motivo_revisao", length = 500)
    private String motivoRevisao;

    @Column(name = "revisado_em")
    private Instant revisadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @OneToMany(mappedBy = "pergunta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicao ASC")
    private List<AlternativaPergunta> alternativas = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = StatusPergunta.PENDENTE;
        Instant agora = Instant.now();
        if (criadoEm == null) criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() { atualizadoEm = Instant.now(); }

    public void adicionarAlternativa(int posicao, String texto) {
        AlternativaPergunta alternativa = new AlternativaPergunta();
        alternativa.setPergunta(this);
        alternativa.setPosicao(posicao);
        alternativa.setTexto(texto);
        alternativas.add(alternativa);
    }

    public UUID getId() { return id; }
    public Tema getTema() { return tema; }
    public void setTema(Tema tema) { this.tema = tema; }
    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }
    public String getEnunciadoNormalizado() { return enunciadoNormalizado; }
    public void setEnunciadoNormalizado(String enunciadoNormalizado) { this.enunciadoNormalizado = enunciadoNormalizado; }
    public int getRespostaCorretaIndice() { return respostaCorretaIndice; }
    public void setRespostaCorretaIndice(int respostaCorretaIndice) { this.respostaCorretaIndice = respostaCorretaIndice; }
    public StatusPergunta getStatus() { return status; }
    public void setStatus(StatusPergunta status) { this.status = status; }
    public OrigemPergunta getOrigem() { return origem; }
    public void setOrigem(OrigemPergunta origem) { this.origem = origem; }
    public DificuldadePergunta getDificuldadeInicial() { return dificuldadeInicial; }
    public void setDificuldadeInicial(DificuldadePergunta dificuldadeInicial) { this.dificuldadeInicial = dificuldadeInicial; }
    public String getFonteReferencia() { return fonteReferencia; }
    public void setFonteReferencia(String fonteReferencia) { this.fonteReferencia = fonteReferencia; }
    public String getModeloIa() { return modeloIa; }
    public void setModeloIa(String modeloIa) { this.modeloIa = modeloIa; }
    public String getVersaoGeracao() { return versaoGeracao; }
    public void setVersaoGeracao(String versaoGeracao) { this.versaoGeracao = versaoGeracao; }
    public Usuario getRevisor() { return revisor; }
    public void setRevisor(Usuario revisor) { this.revisor = revisor; }
    public String getMotivoRevisao() { return motivoRevisao; }
    public void setMotivoRevisao(String motivoRevisao) { this.motivoRevisao = motivoRevisao; }
    public Instant getRevisadoEm() { return revisadoEm; }
    public void setRevisadoEm(Instant revisadoEm) { this.revisadoEm = revisadoEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public List<AlternativaPergunta> getAlternativas() { return alternativas; }
}
