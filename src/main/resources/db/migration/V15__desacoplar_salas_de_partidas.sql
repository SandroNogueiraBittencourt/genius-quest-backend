ALTER TABLE salas ADD COLUMN anfitriao_id UUID REFERENCES usuarios(id);

UPDATE salas s
SET anfitriao_id = p.criador_id
FROM partidas p
WHERE p.id = s.partida_id;

ALTER TABLE salas ALTER COLUMN anfitriao_id SET NOT NULL;
ALTER TABLE salas ALTER COLUMN partida_id DROP NOT NULL;
ALTER TABLE partidas ALTER COLUMN ciclo_id DROP NOT NULL;
