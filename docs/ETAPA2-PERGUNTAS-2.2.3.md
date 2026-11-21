# Etapa 2.2.3 — Curadoria administrativa de perguntas

## Contrato e segurança

Todos os endpoints abaixo exigem `Bearer JWT` **e ROLE_ADMIN**. Endpoints não autenticados recebem HTTP 401; usuários comuns recebem HTTP 403. O código mantém duas barreiras `@PreAuthorize`: controller e service.

| Método | Rota | Objetivo |
|---|---|---|
| GET | `/api/admin/perguntas?status=PENDENTE&temaId=UUID&page=0&size=20` | Lista paginada para revisão; filtros opcionais, tamanho 1–100 |
| GET | `/api/admin/perguntas/{id}` | Exibe detalhes e gabarito ao administrador |
| GET | `/api/admin/perguntas/{id}/historico` | Eventos append-only, com snapshots antes/depois |
| PATCH | `/api/admin/perguntas/{id}/revisar` | Editar uma candidata `PENDENTE` |
| PATCH | `/api/admin/perguntas/{id}/decisao` | `APROVAR`, `REJEITAR` ou `BLOQUEAR` |

**Atenção:** DTO `PerguntaModeracaoDTO` e snapshots de auditoria contêm gabarito. Nunca reutilizar esses objetos em endpoints do jogador. O banco de dados deve permanecer inacessível ao cliente web.

## Exemplos de payload

### Revisão

```json
{
  "enunciado": "Qual planeta é conhecido como planeta vermelho?",
  "alternativas": ["Terra", "Marte", "Vênus", "Netuno"],
  "respostaCorretaIndice": 1,
  "dificuldadeInicial": "FACIL",
  "fonteReferencia": "Livro didático verificado",
  "motivo": "Ajuste de alternativas após revisão humana"
}
```

### Decisão

```json
{"acao":"APROVAR","motivo":"Questão e fonte conferidas"}
```

Rejeição/bloqueio exigem motivo não vazio (máximo de 500 caracteres).

## Regras de transição

- `PENDENTE -> APROVADA` (aprovação, com revalidação de conteúdo)
- `PENDENTE -> REJEITADA` (motivo obrigatório)
- `PENDENTE -> BLOQUEADA` (motivo obrigatório)
- `APROVADA -> BLOQUEADA` (motivo obrigatório)
- Perguntas rejeitadas/bloqueadas são terminais nesta versão. Não há desbloqueio ou aprovação de rejeitadas.
- Só candidatas pendentes podem ter conteúdo/gabarito editado.

O serviço utiliza lock pessimista por pergunta e transações curtas. Cada revisão ou decisão grava um evento V9 com responsável, estado antigo e novo, motivo, data e snapshots. O serviço não expõe exclusão desses eventos. *Esse histórico é append-only na camada de aplicação; administradores com acesso direto ao banco ainda podem alterá-lo. Para trilha com proteção contra adulteração, planejar controles adicionais.*

## Testes

```bash
DB_HOST=127.0.0.1 DB_PORT=5434 DB_NAME=geniusquest_test \
SPRING_PROFILES_ACTIVE=test APP_IA_ENABLED=false \
SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw -B clean verify
```

Esperado: Flyway V9 aplicada **apenas no banco de testes**, sem chamadas pagas ao provedor de IA. Cobertura: 401/403, filtros, gabarito restrito ao ADMIN, edição, aprovação, bloqueio, rejeição, auditoria e prevenção de duplicação.

Não realizar merge em `develop` antes de revisar a migration V9 e o impacto no Render. Esta subetapa não inclui interface administrativa React nem endpoint para disparar geração paga de perguntas.
