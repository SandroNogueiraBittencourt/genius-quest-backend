#!/usr/bin/env python3
"""Smoke test HTTP somente leitura do GeniusQuest implantado no Render.

Nenhum JWT, senha, chave, metodo de escrita ou dado pessoal e utilizado.
Falhas de rede ou contratos HTTP inesperados terminam com codigo 1.
"""
from __future__ import annotations

import json
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

API = "https://genius-quest-api-7h95.onrender.com"
FRONTEND = "https://genius-quest-web.onrender.com"
HEADERS = {"User-Agent": "GeniusQuest-ProdSmoke-ReadOnly/1.0", "Accept": "application/json"}


def consultar(url: str, *, metodo: str = "GET", headers: dict[str, str] | None = None):
    req = Request(url, method=metodo, headers={**HEADERS, **(headers or {})})
    try:
        with urlopen(req, timeout=20) as resp:
            return resp.status, resp.headers, resp.read(4096)
    except HTTPError as ex:
        with ex:
            return ex.code, ex.headers, ex.read(1024)


def exigir(rotulo: str, url: str, esperado: int) -> None:
    status, _, _ = consultar(url)
    print(f"{rotulo}: HTTP {status} (esperado {esperado})")
    if status != esperado:
        raise RuntimeError(f"Contrato HTTP inesperado em {rotulo}: {status}")


def main() -> int:
    # Uma aplicacao no Render Free pode acordar lentamente apos ficar inativa.
    health_error = "sem resposta"
    for tentativa in range(1, 7):
        try:
            status, _, corpo = consultar(API + "/api/health")
            if status == 200:
                payload = json.loads(corpo.decode("utf-8"))
                if payload.get("status") != "UP":
                    raise RuntimeError("Health nao reportou status UP")
                print("Health: HTTP 200 / status UP")
                break
            health_error = f"HTTP {status}"
        except (URLError, TimeoutError) as ex:
            health_error = type(ex).__name__
        if tentativa < 6:
            time.sleep(10)
    else:
        raise RuntimeError(f"API sem health valido: {health_error}")

    # Nenhuma chamada envia Authorization, cookies ou credenciais.
    exigir("Categorias admin sem JWT", API + "/api/categorias?page=0&size=5", 401)
    exigir("Temas sem JWT", API + "/api/temas", 401)
    exigir("Partidas sem JWT", API + "/api/partidas/minhas", 401)
    exigir("IA status admin sem JWT", API + "/api/admin/perguntas/geracao/status", 401)

    origin = FRONTEND
    status, cabecalhos, _ = consultar(
        API + "/api/temas", metodo="OPTIONS",
        headers={"Origin": origin, "Access-Control-Request-Method": "GET"},
    )
    permitido = cabecalhos.get("Access-Control-Allow-Origin")
    print(f"Preflight CORS: HTTP {status}; origem autorizada: {permitido == origin}")
    if status not in (200, 204) or permitido != origin:
        raise RuntimeError("Preflight CORS de producao nao autorizado")

    status, cabecalhos, _ = consultar(FRONTEND + "/", headers={"Accept": "text/html"})
    tipo = cabecalhos.get("Content-Type", "")
    print(f"Frontend: HTTP {status}; tipo HTML: {'text/html' in tipo.lower()}")
    if status != 200 or "text/html" not in tipo.lower():
        raise RuntimeError("Frontend nao respondeu HTML HTTP 200")

    print("SUCESSO: smoke HTTP read-only, autenticacao e CORS sem escritas.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, URLError, TimeoutError, ValueError) as err:
        print(f"FALHA: {err}", file=sys.stderr)
        sys.exit(1)
