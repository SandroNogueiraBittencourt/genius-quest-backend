CREATE TABLE sala_partidas (
    partida_id UUID PRIMARY KEY REFERENCES partidas(id),
    codigo_sala UUID NOT NULL REFERENCES salas(codigo),
    continuacao VARCHAR(12) NOT NULL DEFAULT 'AGUARDANDO'
        CHECK (continuacao IN ('AGUARDANDO', 'APROVADA', 'ENCERRADA')),
    total_jogadores INTEGER CHECK (total_jogadores > 0)
);

INSERT INTO sala_partidas (partida_id, codigo_sala)
SELECT partida_id, codigo FROM salas WHERE partida_id IS NOT NULL;

CREATE TABLE sala_votos_continuacao (
    partida_id UUID NOT NULL REFERENCES sala_partidas(partida_id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    continuar BOOLEAN NOT NULL,
    PRIMARY KEY (partida_id, usuario_id)
);
