CREATE TABLE temas (
    id UUID PRIMARY KEY,
    categoria_id UUID NOT NULL REFERENCES categorias(id),
    autor_id UUID NOT NULL REFERENCES usuarios(id),
    titulo VARCHAR(120) NOT NULL,
    titulo_normalizado VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_temas_status CHECK (status IN ('PENDENTE', 'APROVADO', 'SUSPENSO', 'REMOVIDO'))
);
CREATE UNIQUE INDEX ux_temas_ativos_categoria_titulo
    ON temas (categoria_id, titulo_normalizado) WHERE status <> 'REMOVIDO';
CREATE INDEX idx_temas_status_criado ON temas(status, criado_em);
CREATE INDEX idx_temas_autor ON temas(autor_id);

CREATE TABLE ciclos_votacao (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    inicio_em TIMESTAMPTZ NOT NULL,
    fim_em TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    vencedor_tema_id UUID REFERENCES temas(id),
    encerrado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ciclos_periodo CHECK (fim_em > inicio_em),
    CONSTRAINT ck_ciclos_status CHECK (status IN ('ABERTO', 'ENCERRADO'))
);
-- Não admite dois ciclos abertos com períodos sobrepostos, mesmo sob concorrência.
ALTER TABLE ciclos_votacao ADD CONSTRAINT ex_ciclos_periodos_abertos
    EXCLUDE USING gist (tstzrange(inicio_em, fim_em, '[)') WITH &&)
    WHERE (status = 'ABERTO');

CREATE TABLE votos_tema (
    id UUID PRIMARY KEY,
    tema_id UUID NOT NULL REFERENCES temas(id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    ciclo_id UUID NOT NULL REFERENCES ciclos_votacao(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_voto_usuario_tema_ciclo UNIQUE (usuario_id, tema_id, ciclo_id)
);
CREATE INDEX idx_votos_tema_ciclo ON votos_tema(ciclo_id, tema_id);
