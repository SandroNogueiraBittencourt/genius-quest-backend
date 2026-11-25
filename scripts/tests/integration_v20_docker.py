#!/usr/bin/env python3
"""Teste sintético real: Flyway V12/V13 -> V20, sem acesso ao Render."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import secrets
import subprocess
import time
import sys

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "gq_v20", ROOT / "scripts/geniusquest_v20_backup_restore.py"
)
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

def run(*args: str, input_text: str | None = None) -> str:
    p = subprocess.run(list(args), input=input_text, text=True,
                       check=True, capture_output=True)
    return p.stdout.strip()

def main() -> int:
    # Exercitar os dois baselines, incluindo o V13 que ja esta em producao.
    migrations = ROOT / "src/main/resources/db/migration"
    run("docker", "pull", "postgres:18")
    run("docker", "pull", "flyway/flyway:11")
    for start in (12, 13):
        nonce = secrets.token_hex(5)
        net, pg = "gq_ci_net_" + nonce, "gq_ci_pg_" + nonce
        pwd = secrets.token_urlsafe(20)
        network_created = False
        try:
            run("docker", "network", "create", "--internal", net)
            network_created = True
            run("docker", "run", "-d", "--rm", "--name", pg,
                "--network", net, "-e", f"POSTGRES_DB={mod.PG_DB}",
                "-e", f"POSTGRES_USER={mod.PG_USER}",
                "-e", f"POSTGRES_PASSWORD={pwd}", "postgres:18")
            for _ in range(120):
                if mod.banco_temporario_pronto(pg):
                    break
                time.sleep(.5)
            else:
                raise RuntimeError("PostgreSQL de teste nao finalizou inicializacao")
            def sql(stmt: str) -> str:
                return run("docker", "exec", "-i", pg, "psql", "-X", "-A",
                           "-t", "-v", "ON_ERROR_STOP=1",
                           "-U", mod.PG_USER, "-d", mod.PG_DB, "-c", stmt)
            def migrate(target: int) -> None:
                run("docker", "run", "--rm", "--network", net,
                    "-v", f"{migrations}:/flyway/sql:ro", "flyway/flyway:11",
                    f"-url=jdbc:postgresql://{pg}:5432/{mod.PG_DB}",
                    f"-user={mod.PG_USER}", f"-password={pwd}",
                    f"-target={target}", "-connectRetries=10", "migrate")
            migrate(start)
            sql("""
                INSERT INTO usuarios (id,nome,email,senha,perfil)
                VALUES ('10000000-0000-4000-8000-000000000001',
                 'Usuario de teste','v20-ci@example.invalid','hash-sintetico','USUARIO');
                INSERT INTO categorias (id,nome,status)
                VALUES ('20000000-0000-4000-8000-000000000001','Sintetica','APROVADA');
                INSERT INTO temas (id,categoria_id,autor_id,titulo,titulo_normalizado,status)
                VALUES ('30000000-0000-4000-8000-000000000001',
                  '20000000-0000-4000-8000-000000000001',
                  '10000000-0000-4000-8000-000000000001',
                  'Tema Sintetico','tema sintetico','APROVADO');
                INSERT INTO ciclos_votacao (id,nome,inicio_em,fim_em,status,vencedor_tema_id)
                VALUES ('40000000-0000-4000-8000-000000000001',
                  'Ciclo encerrado', '2026-01-01T00:00:00Z',
                  '2026-01-02T00:00:00Z','ENCERRADO',
                  '30000000-0000-4000-8000-000000000001');
                INSERT INTO partidas (id,ciclo_id,tema_id,criador_id,
                   tema_titulo,categoria_nome,status)
                VALUES ('50000000-0000-4000-8000-000000000001',
                  '40000000-0000-4000-8000-000000000001',
                  '30000000-0000-4000-8000-000000000001',
                  '10000000-0000-4000-8000-000000000001',
                  'Tema Sintetico','Sintetica','PREPARACAO');
                INSERT INTO perguntas (id,tema_id,enunciado,enunciado_normalizado,
                    resposta_correta_indice,status,origem,dificuldade_inicial)
                VALUES ('60000000-0000-4000-8000-000000000001',
                  '30000000-0000-4000-8000-000000000001',
                  'Qual alternativa do teste?','qual alternativa do teste?',
                  0,'APROVADA','MANUAL','FACIL');
                """)
            if start == 13:
                sql("""
                INSERT INTO perguntas_partida
                   (id,partida_id,pergunta_id,ordem,enunciado,
                    alternativa_0,alternativa_1,alternativa_2,alternativa_3,
                    resposta_correta_indice,dificuldade_inicial)
                VALUES ('70000000-0000-4000-8000-000000000001',
                 '50000000-0000-4000-8000-000000000001',
                 '60000000-0000-4000-8000-000000000001',0,
                 'Qual alternativa do teste?','A','B','C','D',0,'FACIL');
                INSERT INTO respostas_partida
                  (id,pergunta_partida_id,usuario_id,resposta_indice,correta)
                VALUES ('80000000-0000-4000-8000-000000000001',
                 '70000000-0000-4000-8000-000000000001',
                 '10000000-0000-4000-8000-000000000001',0,TRUE);
                """)
            sql(mod.SNAPSHOT_SQL)
            migrate(20)
            out=sql(mod.VERIFY_SQL)
            if "OK: dados preexistentes" not in out:
                raise AssertionError("Resultado da verificacao SQL inesperado")
            print(f"SUCESSO: Flyway V{start} -> V20 com dados sinteticos preservados.")
        finally:
            subprocess.run(["docker", "rm", "-f", "-v", pg],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if network_created:
                subprocess.run(["docker", "network", "rm", net],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return 0

if __name__ == "__main__":
    sys.exit(main())
