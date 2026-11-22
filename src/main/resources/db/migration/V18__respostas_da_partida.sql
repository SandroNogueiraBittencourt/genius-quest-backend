ALTER TABLE partidas
    ADD COLUMN pergunta_atual INTEGER NOT NULL DEFAULT 1 CHECK (pergunta_atual BETWEEN 1 AND 10);

CREATE TABLE partida_respostas (
    partida_id UUID NOT NULL,
    numero_pergunta INTEGER NOT NULL,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    alternativa_indice INTEGER NOT NULL CHECK (alternativa_indice BETWEEN 0 AND 3),
    acertou BOOLEAN NOT NULL,
    respondida_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (partida_id, numero_pergunta, usuario_id),
    FOREIGN KEY (partida_id, numero_pergunta) REFERENCES partida_perguntas(partida_id, numero)
);
