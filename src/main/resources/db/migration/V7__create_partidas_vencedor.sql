-- Etapa 2.1: partidas persistidas vinculadas a ciclos encerrados e seus vencedores.
-- Não cria perguntas ou pontuação: estas funções serão adicionadas em novas migrações.
CREATE TABLE partidas (
    id UUID PRIMARY KEY,
    ciclo_id UUID NOT NULL REFERENCES ciclos_votacao(id),
    tema_id UUID NOT NULL REFERENCES temas(id),
    criador_id UUID NOT NULL REFERENCES usuarios(id),
    tema_titulo VARCHAR(120) NOT NULL,
    categoria_nome VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PREPARACAO',
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_partidas_status CHECK (
        status IN ('PREPARACAO', 'PRONTA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')
    )
);
CREATE INDEX idx_partidas_criador_criado ON partidas(criador_id, criado_em DESC);
CREATE INDEX idx_partidas_ciclo ON partidas(ciclo_id);
