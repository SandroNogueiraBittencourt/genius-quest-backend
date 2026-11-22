-- Etapa 2.3.2: congelar conteúdo/gabarito quando a partida começa e registrar
-- exatamente uma resposta por pergunta e jogador. Não altera dados existentes.
-- Índices de gabarito nunca devem ser serializados na API do jogador.
CREATE TABLE perguntas_partida (
    id UUID PRIMARY KEY,
    partida_id UUID NOT NULL REFERENCES partidas(id) ON DELETE CASCADE,
    pergunta_id UUID NOT NULL REFERENCES perguntas(id),
    ordem INTEGER NOT NULL,
    enunciado VARCHAR(500) NOT NULL,
    alternativa_0 VARCHAR(250) NOT NULL,
    alternativa_1 VARCHAR(250) NOT NULL,
    alternativa_2 VARCHAR(250) NOT NULL,
    alternativa_3 VARCHAR(250) NOT NULL,
    resposta_correta_indice INTEGER NOT NULL,
    dificuldade_inicial VARCHAR(10) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_perguntas_partida_ordem CHECK (ordem BETWEEN 0 AND 9),
    CONSTRAINT ck_perguntas_partida_gabarito CHECK (resposta_correta_indice BETWEEN 0 AND 3),
    CONSTRAINT ck_perguntas_partida_dificuldade CHECK (dificuldade_inicial IN ('FACIL','MEDIA','DIFICIL')),
    CONSTRAINT ux_perguntas_partida_ordem UNIQUE (partida_id, ordem),
    CONSTRAINT ux_perguntas_partida_pergunta UNIQUE (partida_id, pergunta_id)
);
CREATE INDEX idx_perguntas_partida_partida ON perguntas_partida(partida_id, ordem);

CREATE TABLE respostas_partida (
    id UUID PRIMARY KEY,
    pergunta_partida_id UUID NOT NULL REFERENCES perguntas_partida(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    resposta_indice INTEGER NOT NULL,
    correta BOOLEAN NOT NULL,
    respondida_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_respostas_partida_indice CHECK (resposta_indice BETWEEN 0 AND 3),
    CONSTRAINT ux_respostas_partida_pergunta_usuario UNIQUE (pergunta_partida_id, usuario_id)
);
CREATE INDEX idx_respostas_partida_usuario ON respostas_partida(usuario_id, respondida_em);
