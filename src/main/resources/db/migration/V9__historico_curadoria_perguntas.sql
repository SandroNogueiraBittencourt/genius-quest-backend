-- Genius Quest · Etapa 2.2.3: registro imutável de decisões de curadoria.
-- Snapshots incluem gabarito: acessíveis apenas para administradores.
CREATE TABLE eventos_curadoria_pergunta (
    id UUID PRIMARY KEY,
    pergunta_id UUID NOT NULL REFERENCES perguntas(id),
    revisor_id UUID NOT NULL REFERENCES usuarios(id),
    acao VARCHAR(20) NOT NULL,
    status_anterior VARCHAR(20) NOT NULL,
    status_novo VARCHAR(20) NOT NULL,
    motivo VARCHAR(500),
    antes TEXT NOT NULL,
    depois TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_evento_curadoria_acao CHECK
        (acao IN ('REVISAR', 'APROVAR', 'REJEITAR', 'BLOQUEAR')),
    CONSTRAINT ck_evento_curadoria_status_anterior CHECK
        (status_anterior IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'BLOQUEADA')),
    CONSTRAINT ck_evento_curadoria_status_novo CHECK
        (status_novo IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'BLOQUEADA'))
);
CREATE INDEX idx_eventos_curadoria_pergunta_data
    ON eventos_curadoria_pergunta(pergunta_id, criado_em, id);
CREATE INDEX idx_eventos_curadoria_revisor
    ON eventos_curadoria_pergunta(revisor_id, criado_em);
