CREATE TABLE resultados_partida (
    partida_id UUID NOT NULL REFERENCES sala_partidas(partida_id),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    nome VARCHAR(100) NOT NULL,
    pontos INTEGER NOT NULL CHECK (pontos >= 0),
    acertos INTEGER NOT NULL CHECK (acertos >= 0),
    erros INTEGER NOT NULL CHECK (erros >= 0),
    PRIMARY KEY (partida_id, usuario_id)
);

CREATE VIEW pontuacao_partida_concluida AS
SELECT r.partida_id, r.usuario_id, u.nome,
       CAST(SUM(CASE WHEN r.acertou THEN CASE q.dificuldade_inicial
           WHEN 'FACIL' THEN 10
           WHEN 'MEDIA' THEN 20
           WHEN 'DIFICIL' THEN 30
       END ELSE 0 END) AS INTEGER) AS pontos,
       CAST(COUNT(*) FILTER (WHERE r.acertou) AS INTEGER) AS acertos,
       CAST(COUNT(*) FILTER (WHERE NOT r.acertou) AS INTEGER) AS erros
FROM partida_respostas r
JOIN sala_partidas sp ON sp.partida_id = r.partida_id
JOIN partidas p ON p.id = sp.partida_id AND p.status = 'CONCLUIDA'
JOIN partida_perguntas q ON q.partida_id = r.partida_id AND q.numero = r.numero_pergunta
JOIN usuarios u ON u.id = r.usuario_id
GROUP BY r.partida_id, r.usuario_id, u.nome;

INSERT INTO resultados_partida (partida_id, usuario_id, nome, pontos, acertos, erros)
SELECT partida_id, usuario_id, nome, pontos, acertos, erros
FROM pontuacao_partida_concluida;
