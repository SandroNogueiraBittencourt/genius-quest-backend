#!/usr/bin/env bash
set -euo pipefail

# Banco descartavel no GitHub Actions; nao usa credenciais do Render/Neon.
if [[ "$GITHUB_ACTIONS" != "true" ]]; then
  echo "ERRO: executavel exclusivamente no runner GitHub Actions isolado." >&2
  exit 1
fi

PG_HOST="127.0.0.1"
PG_PORT="5433"
PG_DB="geniusquest_v12_lab"
PG_USER="geniusquest_v12_ci"
PG_PASSWORD="senha_ci_efemera_sem_uso_real"
MIGRATIONS_DIR="$GITHUB_WORKSPACE/src/main/resources/db/migration"

psql_ci() {
  docker run --rm -i --network host \
    -e "PGPASSWORD=$PG_PASSWORD" postgres:18 \
    psql -X -v ON_ERROR_STOP=1 -h "$PG_HOST" -p "$PG_PORT" \
    -U "$PG_USER" -d "$PG_DB" "$@"
}
migrate() {
  local target="$1"
  docker run --rm --network host \
    -v "$MIGRATIONS_DIR:/flyway/sql:ro" \
    flyway/flyway:11 \
    "-url=jdbc:postgresql://$PG_HOST:$PG_PORT/$PG_DB" \
    "-user=$PG_USER" "-password=$PG_PASSWORD" \
    "-target=$target" "-connectRetries=10" migrate
}

echo "Ensaio da V11 no banco efemero $PG_DB (localhost:$PG_PORT)"
migrate 11
v11="$(psql_ci -At -c "SELECT MAX(version::integer) FROM flyway_schema_history WHERE success")"
if [[ "$v11" != "11" ]]; then
  echo "ERRO: baseline nao chegou a V11: $v11" >&2
  exit 1
fi

# Dados ficticios exercitam referencias existentes e os registros de curadoria.
psql_ci <<'SQL'
BEGIN;
INSERT INTO usuarios (id, nome, email, senha, perfil)
VALUES ('10000000-0000-4000-8000-000000000001',
        'Administrador Sintetico', 'admin-v12@example.invalid', 'hash-ficticio', 'ADMIN');
INSERT INTO categorias (id,nome,status)
VALUES ('20000000-0000-4000-8000-000000000001','Categoria V12 Lab','APROVADA');
INSERT INTO temas (id,categoria_id,autor_id,titulo,titulo_normalizado,status)
VALUES ('30000000-0000-4000-8000-000000000001',
        '20000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        'Tema de Ensaio','tema de ensaio','APROVADO');
INSERT INTO perguntas (id,tema_id,enunciado,enunciado_normalizado,
                       resposta_correta_indice,status,origem,dificuldade_inicial)
VALUES ('40000000-0000-4000-8000-000000000001',
        '30000000-0000-4000-8000-000000000001',
        'Qual alternativa demonstra o teste isolado?',
        'qual alternativa demonstra o teste isolado?',0,
        'BLOQUEADA','MANUAL','FACIL');
INSERT INTO alternativas_pergunta(id,pergunta_id,posicao,texto) VALUES
 ('50000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001',0,'Alternativa ficticia A'),
 ('50000000-0000-4000-8000-000000000002','40000000-0000-4000-8000-000000000001',1,'Alternativa ficticia B'),
 ('50000000-0000-4000-8000-000000000003','40000000-0000-4000-8000-000000000001',2,'Alternativa ficticia C'),
 ('50000000-0000-4000-8000-000000000004','40000000-0000-4000-8000-000000000001',3,'Alternativa ficticia D');
INSERT INTO eventos_curadoria_pergunta
(id,pergunta_id,revisor_id,acao,status_anterior,status_novo,motivo,antes,depois)
VALUES ('60000000-0000-4000-8000-000000000001',
        '40000000-0000-4000-8000-000000000001',
        '10000000-0000-4000-8000-000000000001',
        'BLOQUEAR','APROVADA','BLOQUEADA','Ensaio ficticio',
        '{"status":"APROVADA"}','{"status":"BLOQUEADA"}');
COMMIT;

CREATE TABLE ensaio_snapshot (
  tabela TEXT PRIMARY KEY, quantidade BIGINT NOT NULL, fingerprint TEXT NOT NULL
);
DO $$
DECLARE tab RECORD; linhas BIGINT; resumo TEXT;
BEGIN
 FOR tab IN
    SELECT tablename FROM pg_tables WHERE schemaname='public'
    AND tablename NOT IN ('ensaio_snapshot','flyway_schema_history')
 LOOP
    EXECUTE format(
      'SELECT count(*), md5(COALESCE(string_agg(md5(to_jsonb(t)::text), %L ORDER BY md5(to_jsonb(t)::text)), %L)) FROM public.%I t',
      ',', '', tab.tablename
    ) INTO linhas, resumo;
    INSERT INTO ensaio_snapshot VALUES(tab.tablename, linhas, resumo);
 END LOOP;
END $$;

CREATE TABLE ensaio_flyway_snapshot AS
 SELECT installed_rank, version, checksum, description, type, script, installed_by,
        installed_on, execution_time, success
 FROM flyway_schema_history;
SQL

echo "V11 com dados e snapshots das tabelas preexistentes."
migrate 12
psql_ci <<'SQL'
DO $$
DECLARE t RECORD; linhas BIGINT; resumo TEXT;
BEGIN
 IF (SELECT MAX(version::integer) FROM flyway_schema_history WHERE success) <> 12
  OR (SELECT COUNT(*) FROM flyway_schema_history WHERE version='12' AND success) <> 1
  OR (SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success) <> 0
 THEN RAISE EXCEPTION 'Flyway V12 ausente, duplicada ou com falha'; END IF;

 IF (SELECT COUNT(*) FROM flyway_schema_history) <>
    (SELECT COUNT(*) + 1 FROM ensaio_flyway_snapshot)
 THEN RAISE EXCEPTION 'Historico Flyway inesperado'; END IF;
 IF EXISTS (
   SELECT 1 FROM ensaio_flyway_snapshot old
   FULL JOIN flyway_schema_history new USING (installed_rank)
   WHERE new.installed_rank IS NULL OR old.version IS DISTINCT FROM new.version
     OR old.checksum IS DISTINCT FROM new.checksum
     OR old.description IS DISTINCT FROM new.description
     OR old.type IS DISTINCT FROM new.type
     OR old.script IS DISTINCT FROM new.script
     OR old.installed_by IS DISTINCT FROM new.installed_by
     OR old.installed_on IS DISTINCT FROM new.installed_on
     OR old.execution_time IS DISTINCT FROM new.execution_time
     OR old.success IS DISTINCT FROM new.success
 ) THEN RAISE EXCEPTION 'Historico Flyway V1-V11 alterado'; END IF;

 FOR t IN SELECT * FROM ensaio_snapshot LOOP
   EXECUTE format(
     'SELECT count(*), md5(COALESCE(string_agg(md5(to_jsonb(v)::text), %L ORDER BY md5(to_jsonb(v)::text)), %L)) FROM public.%I v',
     ',', '', t.tabela
   ) INTO linhas, resumo;
   IF linhas IS DISTINCT FROM t.quantidade OR resumo IS DISTINCT FROM t.fingerprint
   THEN RAISE EXCEPTION 'Tabela preexistente alterada: %',t.tabela; END IF;
 END LOOP;

 IF to_regclass('public.ia_geracao_cota_diaria') IS NULL
  OR to_regclass('public.ia_geracao_solicitacoes') IS NULL
  OR to_regclass('public.idx_ia_geracao_solicitacoes_criada') IS NULL
 THEN RAISE EXCEPTION 'Objetos V12 ausentes'; END IF;
 IF (SELECT count(*) FROM ia_geracao_cota_diaria) <> 0
  OR (SELECT count(*) FROM ia_geracao_solicitacoes) <> 0
 THEN RAISE EXCEPTION 'V12 criou registros inesperados'; END IF;
END $$;
SELECT 'SUCESSO: V11 -> V12 preservou dados e Flyway historico' AS resultado;
SQL
echo "ENSAIO ISOLADO V12 CONCLUIDO, SEM ACESSO A PRODUCAO."
