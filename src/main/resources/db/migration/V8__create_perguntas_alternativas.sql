-- Genius Quest · Etapa 2.2.1
-- Perguntas candidatas só se tornam oficiais após aprovação por moderador.
-- O gabarito existe no banco; nunca deve ser enviado na API do jogador.
CREATE TABLE perguntas (
    id UUID PRIMARY KEY,
    tema_id UUID NOT NULL REFERENCES temas(id),
    enunciado VARCHAR(500) NOT NULL,
    enunciado_normalizado VARCHAR(500) NOT NULL,
    resposta_correta_indice INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    origem VARCHAR(10) NOT NULL,
    dificuldade_inicial VARCHAR(10) NOT NULL,
    fonte_referencia VARCHAR(500),
    modelo_ia VARCHAR(100),
    versao_geracao VARCHAR(60),
    revisor_id UUID REFERENCES usuarios(id),
    motivo_revisao VARCHAR(500),
    revisado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_perguntas_status CHECK
        (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'BLOQUEADA')),
    CONSTRAINT ck_perguntas_origem CHECK (origem IN ('IA', 'MANUAL')),
    CONSTRAINT ck_perguntas_dificuldade CHECK
        (dificuldade_inicial IN ('FACIL', 'MEDIA', 'DIFICIL')),
    CONSTRAINT ck_perguntas_resposta CHECK (resposta_correta_indice BETWEEN 0 AND 3)
);
-- Rejeitadas podem ser novamente geradas; candidatas não rejeitadas não se duplicam.
CREATE UNIQUE INDEX ux_perguntas_ativas_tema_enunciado
    ON perguntas(tema_id, enunciado_normalizado)
    WHERE status IN ('PENDENTE', 'APROVADA', 'BLOQUEADA');
CREATE INDEX idx_perguntas_moderacao ON perguntas(status, criado_em);
CREATE INDEX idx_perguntas_tema_status ON perguntas(tema_id, status);

CREATE TABLE alternativas_pergunta (
    id UUID PRIMARY KEY,
    pergunta_id UUID NOT NULL REFERENCES perguntas(id) ON DELETE CASCADE,
    posicao INTEGER NOT NULL,
    texto VARCHAR(250) NOT NULL,
    CONSTRAINT ck_alternativas_posicao CHECK (posicao BETWEEN 0 AND 3),
    CONSTRAINT ux_alternativas_pergunta_posicao UNIQUE (pergunta_id, posicao)
);
CREATE INDEX idx_alternativas_pergunta ON alternativas_pergunta(pergunta_id);
