CREATE TABLE partida_perguntas (
    partida_id UUID NOT NULL REFERENCES partidas(id),
    numero INTEGER NOT NULL CHECK (numero BETWEEN 1 AND 10),
    pergunta_id UUID NOT NULL REFERENCES perguntas(id),
    enunciado VARCHAR(500) NOT NULL,
    alternativas VARCHAR(250)[] NOT NULL CHECK (cardinality(alternativas) = 4),
    resposta_correta_indice INTEGER NOT NULL CHECK (resposta_correta_indice BETWEEN 0 AND 3),
    dificuldade_inicial VARCHAR(10) NOT NULL CHECK (dificuldade_inicial IN ('FACIL', 'MEDIA', 'DIFICIL')),
    PRIMARY KEY (partida_id, numero),
    UNIQUE (partida_id, pergunta_id)
);
