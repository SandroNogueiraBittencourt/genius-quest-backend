-- Etapa 2.2.6: controle duravel de custo, auditoria e idempotencia (UTC).
-- Toda reserva conta para a cota mesmo se o provedor falhar ou der timeout.
CREATE TABLE ia_geracao_cota_diaria (
    dia_utc DATE PRIMARY KEY,
    requisicoes INTEGER NOT NULL DEFAULT 0,
    perguntas INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_ia_cota_requisicoes CHECK (requisicoes BETWEEN 0 AND 4),
    CONSTRAINT ck_ia_cota_perguntas CHECK (perguntas BETWEEN 0 AND 10)
);

CREATE TABLE ia_geracao_solicitacoes (
    chave_idempotencia UUID PRIMARY KEY,
    solicitante VARCHAR(254) NOT NULL,
    tema_id UUID NOT NULL REFERENCES temas(id),
    quantidade INTEGER NOT NULL CHECK (quantidade BETWEEN 1 AND 3),
    status VARCHAR(16) NOT NULL DEFAULT 'RESERVADA',
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluida_em TIMESTAMPTZ,
    CONSTRAINT ck_ia_geracao_status CHECK
        (status IN ('RESERVADA', 'CONCLUIDA', 'FALHOU'))
);
CREATE INDEX idx_ia_geracao_solicitacoes_criada
    ON ia_geracao_solicitacoes (criada_em DESC);
