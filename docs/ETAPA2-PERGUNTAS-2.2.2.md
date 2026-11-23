# Genius Quest — Subetapa 2.2.2: geração IA isolada

## Entrega

- `ProvedorPerguntasIA` desacopla o serviço do fornecedor. O adapter atual usa
  OpenAI **Responses API** com Structured Outputs (JSON Schema estrito).
- `GeracaoPerguntasIAService.gerarCandidatas(temaId, quantidade)` é **interno**:
  ainda não há rota HTTP. A segurança ADMIN virá na subetapa 2.2.3.
- A entrada do modelo contém o título do tema aprovado, o nome da categoria, a
  quantidade solicitada e até 20 enunciados recentes do mesmo tema. Enunciados de
  perguntas pendentes, aprovadas e bloqueadas orientam o modelo a evitar repetições.
- Os lotes são limitados a 1–10 perguntas; exatamente quatro alternativas por pergunta.
- O modelo do banco V8 é reutilizado. Candidatas passam por validação Java, prevenção
  de duplicatas e transação de persistência. Toda pergunta entra como `PENDENTE`.
- A chamada de rede ocorre **fora** das transações do PostgreSQL.
- `store=false`, timeout de requisição de 40 segundos, conexão de 5 segundos,
  uma solicitação simultânea por instância. Não há retries automáticos: evitar
  custos duplicados em caso de timeout incerto.
- Falha total do lote ou resposta inválida => nenhuma candidata é persistida.
- Os testes unitários usam provedor simulado. Não fazem chamadas pagas.

## Ambiente (desativado por padrão)

**Não configure a chave no frontend nem em arquivos versionados.** `APP_IA_ENABLED`
corresponde à propriedade Spring `app.ia.enabled` (relaxed binding).

```bash
export APP_IA_ENABLED=true
read -rsp "OPENAI_API_KEY: " OPENAI_API_KEY; echo
export OPENAI_API_KEY
export OPENAI_MODEL=gpt-6-luna
```

Apenas habilite isso localmente quando quiser testar chamadas pagas. Em produção,
**não ativar ainda**. O model ID deve ser confirmado na sua conta/plano de API.
O limite de simultaneidade é por instância, não substitui quota por usuário,
limites diários de gasto, monitoramento de tokens ou gestão de retries.

## Validação sem IA

```bash
DB_HOST=127.0.0.1 DB_PORT=5434 DB_NAME=geniusquest_test \
DB_PASSWORD="$TEST_DB_PASSWORD" SPRING_PROFILES_ACTIVE=test \
APP_IA_ENABLED=false SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw -B clean verify
```

## Próximo passo

Na 2.2.3 implementar rota ADMIN, auditoria e limites de uso por usuário;
endpoint administrativo de curadoria. Não entregar gabarito em API de jogador.
Na 2.2.4 ligar frontend e testar com mocks + provider real somente de forma
explícita e com orçamento controlado.

## Nota técnica

As perguntas passam apenas por validação estrutural; nenhuma validação automática
comprova a exatidão das respostas. Revisão humana continua obrigatória.
