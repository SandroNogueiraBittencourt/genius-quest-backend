# Etapa 2.2.6 — geração IA protegida para curadoria

## Contrato proposto

- `GET /api/admin/perguntas/geracao/status`: exige JWT ADMIN; não chama o provedor e retorna
  `habilitada`, `maximoPorSolicitacao`, `limiteRequisicoesDiarias`, `requisicoesHoje`,
  `limitePerguntasDiarias`, `perguntasHoje`.
- `POST /api/admin/perguntas/geracao`: exige JWT ADMIN, `Idempotency-Key: UUID` (única por
  tentativa) e JSON `{"temaId":"UUID","quantidade":1}`; 1–3 candidatas por chamada.
  Retorna HTTP 201 `{"ids":["UUID"],"status":"PENDENTE"}`. Não expõe gabarito.

## Proteção de custos

A geração só pode ocorrer se **ambas** estiverem `true`:

- `APP_IA_ENABLED=false` (habilita adaptador real em vez do adaptador inativo)
- `APP_IA_HTTP_ENABLED=false` (habilita invocação HTTP administrativa)

**Defaults são false em todos os ambientes.** Mesmo com ambos os flags ativos,
a ausência de `OPENAI_API_KEY` desabilita o recurso e retorna HTTP 503 sem
consumir quota. Nunca incluir chave real nos testes, CI ou repositório.

Quota global no PostgreSQL, persistente através de reinício e concorrência de instâncias,
implementada pela migration **V12**:
- No máximo **3 perguntas por requisição**;
- No máximo **4 requisições e 10 perguntas por dia UTC**, somando sucessos, falhas e timeouts;
- Linha da quota bloqueada `FOR UPDATE` durante reserva de chave UUID;
- Chave repetida recebe **409**, sem consumir quota adicional e sem repetir chamada paga;
- Tema inexistente ou não aprovado falha com 404/409 **antes** de reservar quota;
- Excesso recebe **429**, sem chamar IA;
- Erros do provedor retornam mensagem segura **502**, não a resposta original;
- Não realizar retry automático (timeout pode já ter custado tokens);
- O CORS permite o cabeçalho `Idempotency-Key` no preflight, além de JWT e JSON;
- O cliente existente usa `max_output_tokens=4500`, timeout 40s, `store=false`,
  JSON Schema estrito e Semaphore de um pedido por instância.

**Importante:** cotas em requisições/perguntas não são um teto monetário exato. O custo
depende dos tokens cobrados pelo modelo escolhido. Definir também alertas e limites
financeiros no painel da OpenAI antes de qualquer ativação real.

## Migração e segurança

- A V12 **cria apenas** `ia_geracao_cota_diaria` e `ia_geracao_solicitacoes`;
  não modifica `perguntas`, `temas`, `usuarios` nem eventos já homologados.
- A migration V12 é aditiva (duas tabelas, índice e FK para temas), sem alteração
  em perguntas ou eventos anteriores. Exigir backup e teste em homologação
  antes de implantar em produção.
- Uma solicitação reservada nunca é reexecutada com a mesma chave após falha;
  administrador deve verificar a fila antes de criar uma nova.
- Todo candidato salvo continua `origem=IA`, `status=PENDENTE` e
  **requer aprovação humana** pelo fluxo já homologado.
- Nenhum merge/deploy ou execução de migration V12 em produção está autorizado nesta subetapa.

## Homologação futura

1. Conferir CI Maven e Docker; testes HTTP 401/403, bloqueio duplo e cota.
2. Revisar V12 e plano de rollback/backup do banco antes de deploy backend.
3. Implantar flags `false` e verificar endpoint de status desabilitado.
4. Habilitar apenas em ambiente isolado com credenciais de teste, budget e
   consentimento para custo real.
5. Exercitar uma geração, verificar IDs `PENDENTE`, depois usar a curadoria humana.
6. Só considerar ativação em produção após homologação e aprovação explícita de custo.

Referências: https://developers.openai.com/api/docs/models/gpt-6-luna
e https://developers.openai.com/api/docs/guides/structured-outputs
