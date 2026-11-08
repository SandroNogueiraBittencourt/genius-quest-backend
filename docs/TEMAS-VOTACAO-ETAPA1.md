# Genius Quest — Etapa 1: temas e votação (backend)

Implementação inicial a partir dos requisitos RF009–RF015 e regras RN006–RN014.
**Não confundir categoria com tema:** categoria organiza, tema possui autor, status, votos e ciclos.

## Fluxo

1. Uma categoria deve estar **APROVADA** antes de receber sugestões de temas.
2. Qualquer usuário autenticado pode sugerir um tema; o backend identifica o autor pelo usuário da sessão e grava como **PENDENTE**.
3. Um ADMIN consulta `/api/temas/moderacao`, aprova/suspende/remove logicamente o tema.
4. Um ADMIN configura um ciclo de votação com `inicioEm` e `fimEm` em ISO 8601 UTC. Ciclos **ABERTO** não podem ter períodos sobrepostos. Um ciclo só está disponível aos jogadores dentro do intervalo configurado.
5. Um usuário elegível pode votar **uma vez em cada tema, por ciclo**, enquanto tema e categoria permanecem aprovados.
6. Ranking do ciclo ordena por número de votos válidos. Em empate, considera tema mais antigo e UUID, de maneira determinística. Como o voto é único por usuário/tema/ciclo, a quantidade de votantes distintos é igual à quantidade de votos.
7. ADMIN encerra um ciclo e persiste o tema vencedor, se houver ao menos um voto elegível.

## Endpoints

| Método | Rota | Autorização |
| --- | --- | --- |
| POST | /api/temas | usuário autenticado |
| GET | /api/temas | usuário autenticado; lista somente aprovados |
| GET | /api/temas/{id} | aprovado, autor ou ADMIN |
| GET | /api/temas/moderacao | ADMIN |
| PATCH | /api/temas/{id} | ADMIN |
| PATCH | /api/temas/{id}/aprovar | ADMIN |
| PATCH | /api/temas/{id}/suspender | ADMIN |
| PATCH | /api/temas/{id}/remover | ADMIN |
| POST | /api/temas/{id}/votos | usuário autenticado |
| POST | /api/ciclos-votacao | ADMIN |
| GET | /api/ciclos-votacao/atual | usuário autenticado |
| GET | /api/ciclos-votacao/{id}/ranking | usuário autenticado |
| PATCH | /api/ciclos-votacao/{id}/encerrar | ADMIN |

### Exemplo de cadastro de tema

```json
{"categoriaId":"UUID_DA_CATEGORIA_APROVADA","titulo":"Xadrez brasileiro","descricao":"História e estratégia"}
```

### Exemplo de ciclo (ADMIN)

```json
{"nome":"Semana 1","inicioEm":"2026-11-09T00:00:00Z","fimEm":"2026-11-16T00:00:00Z"}
```

## Integridade

- `temas` tem chave estrangeira para `usuarios` e `categorias`.
- `votos_tema`: constraint `UNIQUE (usuario_id, tema_id, ciclo_id)` contra votos duplicados sob concorrência.
- `ciclos_votacao`: exclusion constraint GiST impede sobreposição de intervalos de ciclos abertos.
- A linha de ciclo é bloqueada durante voto e encerramento para prevenir voto após fechamento.
- A API devolve 401 sem autenticação, 403 em moderação não autorizada, 404 para recursos ausentes e 409 para conflitos.

## Limitações e próximos PRs

- O frontend de temas/votos ainda não está conectado. Será feito em PR próprio.
- Duplicidade normalizada por título/categoria está implementada; detecção de similaridade semântica permanece pendente (RF010).
- Critério de decisão **manual** da moderação em desempates remanescentes ainda não está disponível; empate está resolvido por data/UUID.
- O encerramento pode ser antecipado pelo ADMIN. Não há agendador de fechamento automático.
- Ranking recalcula contagens conforme estado de moderação atual. Para históricos imutáveis, consolidar snapshots de ranking ao encerrar.
- Paginação, rate limiting e trilha de auditoria de moderação são melhorias necessárias antes de alta escala.
- Não executar migration no PostgreSQL de produção antes da aprovação, teste e backup.
