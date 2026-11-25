CREATE TABLE salas (
    codigo UUID PRIMARY KEY,
    partida_id UUID NOT NULL UNIQUE REFERENCES partidas(id)
);

CREATE TABLE sala_participantes (
    codigo_sala UUID NOT NULL REFERENCES salas(codigo),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    entrou_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (codigo_sala, usuario_id)
);
