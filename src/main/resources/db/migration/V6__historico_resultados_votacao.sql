-- Fotografia de ranking por ciclo. O encerramento passa a gravar o resultado
-- para que títulos e status futuros não mudem a classificação encerrada.
CREATE TABLE resultados_ciclo_votacao (
    id UUID PRIMARY KEY,
    ciclo_id UUID NOT NULL REFERENCES ciclos_votacao(id),
    tema_id UUID NOT NULL REFERENCES temas(id),
    posicao INTEGER NOT NULL CHECK (posicao > 0),
    titulo_tema VARCHAR(120) NOT NULL,
    votos BIGINT NOT NULL CHECK (votos >= 0),
    votantes_distintos BIGINT NOT NULL CHECK (votantes_distintos >= 0),
    reconstruido BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ux_resultados_ciclo_tema UNIQUE (ciclo_id, tema_id),
    CONSTRAINT ux_resultados_ciclo_posicao UNIQUE (ciclo_id, posicao)
);

CREATE INDEX idx_resultados_ciclo_ordem ON resultados_ciclo_votacao(ciclo_id, posicao);

-- A elegibilidade dos temas nos ciclos fechados antes desta migration não
-- pode ser reconstruída integralmente. Somente temas que receberam votos
-- são reconstituídos; a UI sinaliza este limite de histórico.
ALTER TABLE ciclos_votacao ADD COLUMN ranking_reconstruido BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE ciclos_votacao SET ranking_reconstruido = TRUE WHERE status = 'ENCERRADO';

WITH contagens AS (
    SELECT c.id AS ciclo_id, t.id AS tema_id, t.titulo, t.criado_em,
           COUNT(v.id)::BIGINT AS total
    FROM ciclos_votacao c
    JOIN votos_tema v ON v.ciclo_id = c.id
    JOIN temas t ON t.id = v.tema_id
    WHERE c.status = 'ENCERRADO'
    GROUP BY c.id, t.id, t.titulo, t.criado_em
), ordenados AS (
    SELECT ciclo_id, tema_id, titulo, total,
           ROW_NUMBER() OVER (
               PARTITION BY ciclo_id ORDER BY total DESC, criado_em ASC, tema_id ASC
           ) AS posicao
    FROM contagens
)
INSERT INTO resultados_ciclo_votacao (
    id, ciclo_id, tema_id, posicao, titulo_tema, votos, votantes_distintos, reconstruido
)
SELECT gen_random_uuid(), ciclo_id, tema_id, posicao, titulo, total, total, TRUE
FROM ordenados;
