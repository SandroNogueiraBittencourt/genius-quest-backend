#!/usr/bin/env python3
"""Genius Quest: exportar PostgreSQL Render e validar Flyway V11 -> V12 localmente.

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
    if version != "11":
        raise ValidacaoError(f"Banco de origem na versao Flyway {version}, esperada V11. Sem dump.")
    failures = command(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", *conn,
                        "-c", "SELECT COUNT(*) FROM public.flyway_schema_history WHERE NOT success"],
                       env=env, capture=True).stdout.strip()
    if failures != "0":
        raise ValidacaoError(f"Historico Flyway de origem tem {failures} falha(s). Sem dump.")
    BACKUP_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(BACKUP_DIR, 0o700)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = BACKUP_DIR / f"geniusquest_pre_v12_{stamp}.dump"
    with path.open("xb"):
        pass
    os.chmod(path, 0o600)
    print(f"Gerando backup custom PostgreSQL (Flyway V11): {path}")
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
CREATE TABLE gq_v12_snapshot (
  tabela TEXT PRIMARY KEY, quantidade BIGINT NOT NULL, fingerprint TEXT NOT NULL
);
DO $$
DECLARE tab RECORD; linhas BIGINT; resumo TEXT;
BEGIN
 FOR tab IN SELECT tablename FROM pg_tables
            WHERE schemaname='public'
              AND tablename NOT IN ('gq_v12_snapshot', 'flyway_schema_history')
 LOOP
  EXECUTE format(
    'SELECT count(*), md5(COALESCE(string_agg(md5(to_jsonb(t)::text), %L ORDER BY md5(to_jsonb(t)::text)), %L)) FROM public.%I t',
    ',', '', tab.tablename
  ) INTO linhas, resumo;
  INSERT INTO gq_v12_snapshot VALUES(tab.tablename, linhas, resumo);
 END LOOP;
END $$;
CREATE TABLE gq_v12_flyway_snapshot AS
 SELECT installed_rank, version, checksum, description, type, script,
        installed_by, installed_on, execution_time, success
 FROM public.flyway_schema_history;
"""

VERIFY_SQL = r"""
DO $$
DECLARE t RECORD; linhas BIGINT; resumo TEXT;
BEGIN
 IF (SELECT MAX(version::integer) FROM flyway_schema_history WHERE success) <> 12
    OR (SELECT COUNT(*) FROM flyway_schema_history WHERE version='12' AND success) <> 1
    OR (SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success) > 0
 THEN RAISE EXCEPTION 'Flyway V12 nao aplicada corretamente'; END IF;
 IF (SELECT COUNT(*) FROM flyway_schema_history) <>
       (SELECT COUNT(*) + 1 FROM gq_v12_flyway_snapshot)
 THEN RAISE EXCEPTION 'Historico Flyway foi modificado'; END IF;
 IF EXISTS (
   SELECT 1 FROM gq_v12_flyway_snapshot old
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
 ) THEN RAISE EXCEPTION 'Historico Flyway V1-V11 diverge'; END IF;
 FOR t IN SELECT * FROM gq_v12_snapshot LOOP
  EXECUTE format(
    'SELECT count(*), md5(COALESCE(string_agg(md5(to_jsonb(v)::text), %L ORDER BY md5(to_jsonb(v)::text)), %L)) FROM public.%I v',
    ',', '', t.tabela
  ) INTO linhas, resumo;
  IF linhas IS DISTINCT FROM t.quantidade OR resumo IS DISTINCT FROM t.fingerprint
  THEN RAISE EXCEPTION 'Divergencia de dados na tabela preexistente: %', t.tabela; END IF;
 END LOOP;
 IF to_regclass('public.ia_geracao_cota_diaria') IS NULL
    OR to_regclass('public.ia_geracao_solicitacoes') IS NULL
 THEN RAISE EXCEPTION 'Tabelas esperadas da V12 ausentes'; END IF;
 IF (SELECT count(*) FROM ia_geracao_cota_diaria) <> 0
    OR (SELECT count(*) FROM ia_geracao_solicitacoes) <> 0
 THEN RAISE EXCEPTION 'V12 introduziu registros nao esperados'; END IF;
END $$;
SELECT 'OK: dados preexistentes, historico Flyway e V12 validados' AS resultado;
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
    if not (migrations / "V12__quota_e_idempotencia_geracao_ia.sql").is_file():
        raise ValidacaoError("A V12 esperada nao existe na pasta de migrations.")
    manifest = Path(str(archive) + ".sha256")
    if not manifest.is_file():
        raise ValidacaoError("Manifesto .sha256 nao encontrado; nao executar restore.")
    line = manifest.read_text(encoding="utf-8").strip()
    match = re.fullmatch(r"([0-9a-f]{64})  (\S+)", line)
    if not match or match.group(2) != archive.name or sha256(archive) != match.group(1):
        raise ValidacaoError("Manifesto ou SHA-256 do backup invalido.")
    nonce = secrets.token_hex(5)
    net = "gq_v12_lab_" + nonce
    pg = "gq_v12_pg_" + nonce
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
        if version.stdout.strip().splitlines()[-1].strip() != "11":
            raise ValidacaoError("Dump restaurado nao esta no estado Flyway V11; V12 nao aplicada.")
        print("Criando snapshots das tabelas e do historico V11 no banco isolado.")
        psql(pg, SNAPSHOT_SQL)
        print("Executando Flyway V12 sobre a copia restaurada (nunca na origem).")
        command(["docker", "run", "--rm", "--network", net,
                 "--volume", f"{migrations}:/flyway/sql:ro", "flyway/flyway:11",
                 f"-url=jdbc:postgresql://{pg}:5432/{PG_DB}",
                 f"-user={PG_USER}", f"-password={tmp_password}",
                 "-target=12", "-connectRetries=10", "migrate"])
        print("Verificando integridade de todas as tabelas antigas e historico Flyway.")
        psql(pg, VERIFY_SQL)
        print("SUCESSO: backup restaurado e V11 -> V12 validado em isolamento Docker.")
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
    validate = subs.add_parser("restore-test", help="restaurar dump em Docker isolado e testar V12")
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
