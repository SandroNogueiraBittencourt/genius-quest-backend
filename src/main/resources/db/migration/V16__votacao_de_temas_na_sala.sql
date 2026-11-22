ALTER TABLE salas ADD COLUMN votacao_aberta BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sala_votos (
    codigo_sala UUID NOT NULL,
    usuario_id UUID NOT NULL,
    tema_id UUID NOT NULL REFERENCES temas(id),
    PRIMARY KEY (codigo_sala, usuario_id),
    FOREIGN KEY (codigo_sala, usuario_id)
        REFERENCES sala_participantes(codigo_sala, usuario_id)
);
