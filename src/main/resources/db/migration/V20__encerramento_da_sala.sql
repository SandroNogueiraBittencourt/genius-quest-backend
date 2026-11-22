ALTER TABLE salas ADD COLUMN encerrada BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE salas s SET encerrada = TRUE, votacao_aberta = FALSE
FROM sala_partidas sp
WHERE s.partida_id = sp.partida_id AND sp.continuacao = 'ENCERRADA';
