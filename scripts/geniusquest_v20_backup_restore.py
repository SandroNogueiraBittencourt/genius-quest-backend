#!/usr/bin/env python3
"""Genius Quest: exportar PostgreSQL Render e ensaiar Flyway V12/V13 -> V20 localmente.

As operacoes no banco Render sao somente leitura (psql SELECT e pg_dump).
O restore e a migration ocorrem exclusivamente em Docker temporario isolado.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote, urlsplit

BACKUP_DIR = Path.home() / "Backups" / "geniusquest" / "render"
PG_USER = "geniusquest_restore"
PG_DB = "geniusquest_restore"


class ValidacaoError(Exception):
    pass


def command(args: list[str], *, env: dict[str, str] | None = None,
            input_file=None, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        args, env=env, stdin=input_file,
        check=True, text=capture, capture_output=capture,
    )


def require_bin(name: str) -> None:
    if not shutil.which(name):
        raise ValidacaoError(f"Comando ausente: {name}. Instale-o antes de prosseguir.")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def read_db_conn() -> tuple[dict[str, str], list[str]]:
    print("Cole a External Database URL do Render (a entrada nao sera exibida).")
    raw = getpass.getpass("External Database URL: ").strip()
    try:
        url = urlsplit(raw)
        if url.scheme not in ("postgres", "postgresql"):
            raise ValidacaoError("A URL deve iniciar com postgres:// ou postgresql://.")
        if not all((url.hostname, url.username, url.password, url.path.strip("/"))):
            raise ValidacaoError("URL sem host, usuario, senha ou nome do banco.")
        if url.query and any(k.lower() == "sslmode" and v.lower() == "disable"
                             for k, v in (q.split("=", 1) for q in url.query.split("&") if "=" in q)):
            raise ValidacaoError("URL de producao com sslmode=disable nao e aceita.")
        host = url.hostname
        port = str(url.port or 5432)
        user = unquote(url.username)
        pwd = unquote(url.password)
        name = unquote(url.path.lstrip("/"))
        if not name or "/" in name:
            raise ValidacaoError("Nome de banco na URL invalido.")
        env = os.environ.copy()
        env["PGPASSWORD"] = pwd
        env["PGSSLMODE"] = "require"
        env["PGCONNECT_TIMEOUT"] = "12"
        # Nunca colocar o segredo na linha de comando.
        conn = ["-h", host, "-p", port, "-U", user, "-d", name]
        return env, conn
    except ValueError as ex:
        raise ValidacaoError("URL de conexao invalida.") from ex
    finally:
        del raw


def backup() -> None:
    for app in ("pg_dump", "pg_restore", "psql"):
        require_bin(app)
    env, conn = read_db_conn()
    check = command(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", *conn,
                     "-c", "SELECT COALESCE(MAX(version::integer), 0) "
                     "FROM public.flyway_schema_history WHERE success"],
                    env=env, capture=True)
    version = check.stdout.strip()
    if version not in ("12", "13"):
        raise ValidacaoError(f"Banco de origem Flyway {version}; esperada V12 ou V13. Sem dump.")
    failures = command(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", *conn,
                        "-c", "SELECT COUNT(*) FROM public.flyway_schema_history WHERE NOT success"],
                       env=env, capture=True).stdout.strip()
    if failures != "0":
        raise ValidacaoError(f"Historico Flyway de origem tem {failures} falha(s). Sem dump.")
    BACKUP_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(BACKUP_DIR, 0o700)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = BACKUP_DIR / f"geniusquest_pre_v20_{stamp}.dump"
    with path.open("xb"):
        pass
    os.chmod(path, 0o600)
    print(f"Gerando backup custom PostgreSQL (Flyway V{version}): {path}")
    try:
        command(["pg_dump", *conn, "--format=custom", "--no-owner",
                 "--no-privileges", "--file", str(path)], env=env)
        if not path.stat().st_size:
            raise ValidacaoError("Backup resultou em arquivo vazio.")
        command(["pg_restore", "--list", str(path)], capture=True)
        digest = sha256(path)
        manifest = Path(str(path) + ".sha256")
        with manifest.open("x", encoding="utf-8") as f:
            os.chmod(manifest, 0o600)
            f.write(f"{digest}  {path.name}\n")
    except Exception:
        path.unlink(missing_ok=True)
        raise
    finally:
        env.pop("PGPASSWORD", None)
    print(f"Backup pronto: {path}")
    print(f"Tamanho: {path.stat().st_size:,} bytes | SHA256: {digest}")
    print(f"Manifesto de integridade: {manifest}")
    print("Falta executar restore-test com esse arquivo para validar a recuperacao.")


SNAPSHOT_SQL = r"""
CREATE TABLE gq_v20_snapshot (
  tabela TEXT PRIMARY KEY, quantidade BIGINT NOT NULL, fingerprint TEXT NOT NULL
);
DO $$
DECLARE tab RECORD; linhas BIGINT; resumo TEXT;
BEGIN
 FOR tab IN SELECT tablename FROM pg_tables
            WHERE schemaname='public'
              AND tablename NOT IN ('gq_v20_snapshot', 'flyway_schema_history')
 LOOP
  EXECUTE format(
    'SELECT count(*), md5(COALESCE(string_agg(md5(to_jsonb(t)::text), %L ORDER BY md5(to_jsonb(t)::text)), %L)) FROM public.%I t',
    ',', '', tab.tablename
  ) INTO linhas, resumo;
  INSERT INTO gq_v20_snapshot VALUES(tab.tablename, linhas, resumo);
 END LOOP;
END $$;
CREATE TABLE gq_v20_flyway_snapshot AS
 SELECT installed_rank, version, checksum, description, type, script,
        installed_by, installed_on, execution_time, success
 FROM public.flyway_schema_history;
"""

VERIFY_SQL = r"""
DO $$
DECLARE
  t RECORD;
  linhas BIGINT;
  resumo TEXT;
  expressao TEXT;
  origem INTEGER;
BEGIN
 SELECT MAX(version::integer) INTO origem
 FROM gq_v20_flyway_snapshot WHERE success;
 IF origem NOT IN (12, 13) THEN
   RAISE EXCEPTION 'Origem Flyway inesperada: %', origem;
 END IF;
 IF (SELECT MAX(version::integer) FROM flyway_schema_history WHERE success) <> 20
    OR (SELECT COUNT(*) FROM flyway_schema_history WHERE version='20' AND success) <> 1
    OR (SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success) > 0
 THEN RAISE EXCEPTION 'Flyway V20 nao aplicada corretamente'; END IF;
 IF (SELECT COUNT(*) FROM flyway_schema_history) <>
       (SELECT COUNT(*) + (20 - origem) FROM gq_v20_flyway_snapshot)
 THEN RAISE EXCEPTION 'Quantidade de migrations inesperada'; END IF;
 IF (SELECT COUNT(*) FROM flyway_schema_history
     WHERE version::integer > origem AND version::integer <= 20 AND success) <> (20 - origem)
 THEN RAISE EXCEPTION 'Sequencia Flyway incompleta'; END IF;
 IF EXISTS (
   SELECT 1 FROM gq_v20_flyway_snapshot old
   LEFT JOIN flyway_schema_history new USING (installed_rank)
   WHERE new.installed_rank IS NULL OR old.version IS DISTINCT FROM new.version
     OR old.checksum IS DISTINCT FROM new.checksum
     OR old.description IS DISTINCT FROM new.description
     OR old.type IS DISTINCT FROM new.type
     OR old.script IS DISTINCT FROM new.script
     OR old.installed_by IS DISTINCT FROM new.installed_by
     OR old.installed_on IS DISTINCT FROM new.installed_on
     OR old.execution_time IS DISTINCT FROM new.execution_time
     OR old.success IS DISTINCT FROM new.success
 ) THEN RAISE EXCEPTION 'Historico Flyway preexistente divergiu'; END IF;
 FOR t IN SELECT * FROM gq_v20_snapshot LOOP
  IF to_regclass(format('public.%I', t.tabela)) IS NULL THEN
    RAISE EXCEPTION 'Tabela antiga desapareceu: %', t.tabela; END IF;
  -- Na V18 uma coluna nova, pergunta_atual, foi adicionada a partidas.
  -- Sua exclusao somente do fingerprint preserva todas as colunas V12/V13.
  expressao := CASE WHEN t.tabela = 'partidas'
    THEN 'to_jsonb(v) - ''pergunta_atual'''
    ELSE 'to_jsonb(v)' END;
  EXECUTE format(
    'SELECT count(*), md5(COALESCE(string_agg(md5((%s)::text), %L ORDER BY md5((%s)::text)), %L)) FROM public.%I v',
    expressao, ',', expressao, '', t.tabela
  ) INTO linhas, resumo;
  IF linhas IS DISTINCT FROM t.quantidade OR resumo IS DISTINCT FROM t.fingerprint
  THEN RAISE EXCEPTION 'Dados antigos divergiram na tabela: %', t.tabela; END IF;
 END LOOP;
 IF to_regclass('public.perguntas_partida') IS NULL
    OR to_regclass('public.respostas_partida') IS NULL
    OR to_regclass('public.salas') IS NULL
    OR to_regclass('public.sala_participantes') IS NULL
    OR to_regclass('public.sala_votos') IS NULL
    OR to_regclass('public.partida_perguntas') IS NULL
    OR to_regclass('public.partida_respostas') IS NULL
    OR to_regclass('public.sala_partidas') IS NULL
    OR to_regclass('public.sala_votos_continuacao') IS NULL
 THEN RAISE EXCEPTION 'Tabelas esperadas nas V13 a V19 ausentes'; END IF;
 IF NOT EXISTS (
   SELECT 1 FROM information_schema.columns
   WHERE table_schema='public' AND table_name='salas' AND column_name='encerrada'
 ) THEN RAISE EXCEPTION 'Coluna salas.encerrada V20 ausente'; END IF;
 IF EXISTS (SELECT 1 FROM partidas WHERE pergunta_atual <> 1) THEN
   RAISE EXCEPTION 'Valores inesperados da coluna adicionada pergunta_atual'; END IF;
END $$;
SELECT 'OK: dados preexistentes, historico Flyway e V20 validados' AS resultado;
"""


def psql(docker_container: str, sql: str, *, capture: bool = False) -> subprocess.CompletedProcess:
    return command(["docker", "exec", "-i", docker_container,
                    "psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-U", PG_USER,
                    "-d", PG_DB, "-c", sql], capture=capture)


def restore_test(archive: Path, migrations: Path) -> None:
    for app in ("docker", "pg_restore"):
        require_bin(app)
    archive = archive.expanduser().resolve(strict=True)
    migrations = migrations.expanduser().resolve(strict=True)
    if not archive.is_file() or not migrations.is_dir():
        raise ValidacaoError("Informe backup legivel e pasta de migrations valida.")
    if not (migrations / "V20__encerramento_da_sala.sql").is_file():
        raise ValidacaoError("A V20 esperada nao existe na pasta de migrations.")
    manifest = Path(str(archive) + ".sha256")
    if not manifest.is_file():
        raise ValidacaoError("Manifesto .sha256 nao encontrado; nao executar restore.")
    line = manifest.read_text(encoding="utf-8").strip()
    match = re.fullmatch(r"([0-9a-f]{64})  (\S+)", line)
    if not match or match.group(2) != archive.name or sha256(archive) != match.group(1):
        raise ValidacaoError("Manifesto ou SHA-256 do backup invalido.")
    nonce = secrets.token_hex(5)
    net = "gq_v20_lab_" + nonce
    pg = "gq_v20_pg_" + nonce
    tmp_password = secrets.token_urlsafe(20)  # Credencial APENAS do container efemero.
    network_created = False
    container_started = False
    try:
        print("Criando rede Docker interna, sem saida para Internet e sem portas no host.")
        command(["docker", "network", "create", "--internal", net])
        network_created = True
        command(["docker", "run", "--detach", "--rm", "--name", pg,
                 "--network", net,
                 "--env", f"POSTGRES_DB={PG_DB}",
                 "--env", f"POSTGRES_USER={PG_USER}",
                 "--env", f"POSTGRES_PASSWORD={tmp_password}", "postgres:18"])
        container_started = True
        for _ in range(80):
            status = subprocess.run(["docker", "exec", pg, "pg_isready", "-U", PG_USER,
                                     "-d", PG_DB], capture_output=True)
            if status.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise ValidacaoError("PostgreSQL temporario nao inicializou.")
        print("Restaurando o dump no PostgreSQL 18 ISOLADO (nao em producao).")
        with archive.open("rb") as f:
            command(["docker", "exec", "-i", pg, "pg_restore", "-U", PG_USER,
                     "-d", PG_DB, "--no-owner", "--no-privileges",
                     "--exit-on-error"], input_file=f)
        version = psql(pg, "SELECT COALESCE(MAX(version::integer),0) "
                           "FROM flyway_schema_history WHERE success", capture=True)
        origem = version.stdout.strip().splitlines()[-1].strip()
        if origem not in ("12", "13"):
            raise ValidacaoError("Dump restaurado nao esta em Flyway V12 ou V13; nenhuma migration aplicada.")
        falhas = psql(pg, "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", capture=True)
        if falhas.stdout.strip().splitlines()[-1].strip() != "0":
            raise ValidacaoError("Dump restaurado tem falhas Flyway; nenhuma migration aplicada.")
        print(f"Criando snapshots das tabelas V{origem} e do historico no banco isolado.")
        psql(pg, SNAPSHOT_SQL)
        print(f"Executando Flyway V{origem} -> V20 sobre a copia restaurada (nunca na origem).")
        command(["docker", "run", "--rm", "--network", net,
                 "--volume", f"{migrations}:/flyway/sql:ro", "flyway/flyway:11",
                 f"-url=jdbc:postgresql://{pg}:5432/{PG_DB}",
                 f"-user={PG_USER}", f"-password={tmp_password}",
                 "-target=20", "-connectRetries=10", "migrate"])
        print("Verificando integridade de todas as tabelas antigas e historico Flyway.")
        psql(pg, VERIFY_SQL)
        print(f"SUCESSO: backup restaurado e V{origem} -> V20 validado em isolamento Docker.")
    finally:
        if container_started:
            subprocess.run(["docker", "rm", "-f", "--volumes", pg],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if network_created:
            subprocess.run(["docker", "network", "rm", net],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print("Ambiente temporario removido. O dump local foi preservado.")


def main() -> int:
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    subs.add_parser("backup", help="exportar Render com pg_dump (somente leitura)")
    validate = subs.add_parser("restore-test", help="restaurar dump em Docker isolado e testar V12/V13 para V20")
    validate.add_argument("--backup", required=True, type=Path,
                          help="caminho para .dump gerado pelo comando backup")
    validate.add_argument("--migrations-dir", type=Path,
                          default=Path(__file__).resolve().parent.parent /
                                  "src/main/resources/db/migration",
                          help="pasta db/migration do backend (padrao: junto ao script no repo)")
    args = parser.parse_args()
    try:
        if args.action == "backup":
            backup()
        else:
            restore_test(args.backup, args.migrations_dir)
    except (ValidacaoError, subprocess.CalledProcessError, OSError) as ex:
        print(f"ERRO: {ex}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
