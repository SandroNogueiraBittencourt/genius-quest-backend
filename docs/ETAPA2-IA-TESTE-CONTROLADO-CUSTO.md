# Etapa 2.2.8 — Preparacao para 1 pergunta real, com controle de custo

> **Estado deste PR: preparacao apenas.** Nao ativar flags, cadastrar chave
> no Render nem fazer chamada remunerada antes de autorizacao especifica.

## Validacao tecnica (22/11/2026)

- Modelo escolhido: `gpt-6-luna` (ID oficial OpenAI, Responses API).
- Endpoint: `POST https://api.openai.com/v1/responses` **exclusivamente no backend**.
- O adaptador usa `text.format=json_schema`, `strict=true`, e
  `additionalProperties=false` em todos os objetos; `store=false`.
- `reasoning.effort=none`: adequado ao caso de quiz curto, reduz consumo de
  tokens de raciocinio e aumenta a margem do limite de saída.
- `max_output_tokens=4500`: teto por chamada para tokens de saída, **inclui**
  tokens de raciocínio; nao limita os tokens de entrada nem constitui teto em USD.
- Timeout 40 segundos, sem retry automatico. Falha/timeout pode gerar custo.
- Provedor retorna perguntas candidatas; a persistencia exige `PENDENTE`.
  A publicacao depende de aprovacao humana separada.

Documentacao oficial:
- Modelo: https://developers.openai.com/api/docs/models/gpt-6-luna
- Estrutura: https://developers.openai.com/api/docs/guides/structured-outputs
- Precos: https://developers.openai.com/api/docs/pricing
- Limite de gasto: https://help.openai.com/en/articles/9186755-managing-projects-in-the-api-platform

## Custos estimados (USD, tarifa Standard divulgada em 22/11/2026)

`gpt-6-luna`:
- Entrada: USD 0.10 / 1.000.000 tokens.
- Saida: USD 0.50 / 1.000.000 tokens.
- Exemplo ilustrativo: 2.000 entrada + 1.500 saida = USD 0.00095.
- Caso conservador ilustrativo: 3.000 entrada + 4.500 saida
  = USD 0.00255, **nao e teto absoluto** (entrada e variavel).
- Os 4 pedidos diarios da aplicacao, mesmo no exemplo conservador,
  custariam cerca de USD 0.0102, sem contar outras operacoes ou ajustes.

Preco depende de tokens efetivos, tier, regiao, impostos e configuracoes.
A assinatura ChatGPT nao financia automaticamente o uso da API.

## Controles antes de qualquer chamada real

- [x] Testar login / autorizacao: 401 anonimo, 403 usuario comum,
  200 administrador (verificacoes em producao feitas em 22/11).
- [x] Banco Flyway V12 e quotas persistentes, sem registros anteriores de IA.
- [x] Dupla chave de ativacao, atualmente `APP_IA_ENABLED=false`
  e `APP_IA_HTTP_ENABLED=false`.
- [ ] Criar / selecionar projeto separado no painel OpenAI API.
- [ ] Verificar faturamento e permissao do projeto para `gpt-6-luna`.
- [ ] Configurar limite mensal de teste (sugestao: **USD 1**),
  ativar explicitamente **Enforce a hard limit** e alertas.
  **Limite financeiro pode nao interromper imediatamente**, logo
  a cobranca pode ultrapassar ligeiramente o valor.
- [ ] Criar chave com escopo do projeto, idealmente permissao restrita
  ao uso necessario. Nao postar, commitar, enviar por chat ou colocar no frontend.
- [ ] Cadastrar `OPENAI_API_KEY` exclusivamente no ambiente do backend Render;
  `OPENAI_MODEL=gpt-6-luna`. **Manter as duas flags false** ate aprovacao final.
  Alterar ambiente Render pode iniciar deploy; verificar a aplicacao apos isso.
- [ ] Obter autorizacao explicita **para ativar as flags e gerar 1 pergunta**
  e escolher tema `APROVADO`.
- [ ] Executar **apenas 1 solicitacao de quantidade=1**, com UUID de idempotencia
  e confirmacao de custo pela UI administrativa; sem retry.
- [ ] Confirmar retorno 201, um UUID, status `PENDENTE`, origem `IA`,
  quatro alternativas, dificuldade e revisao humana antes de aprovar.
- [ ] Conferir uso real no projeto OpenAI e `ia_geracao_cota_diaria` no banco.
- [ ] Desativar novamente as duas flags apos o teste, verificando o status
  administrativo como `habilitada=false`.

## Limites locais existentes

O backend restringe **ate 3 perguntas por solicitacao**, **4 solicitacoes
por dia UTC** e **10 perguntas por dia UTC**, persistidos no PostgreSQL,
inclusive em caso de erro/timeout. Repetir o mesmo `Idempotency-Key` retorna
conflito e nao autoriza novo lote. Essas cotas nao sao um limite financeiro
automatico em USD: o controle financeiro fica na OpenAI Platform.

## O que nao deve acontecer nesta etapa

- Nenhum deploy do backend neste PR, pois ele altera adaptador e testes.
- Nenhuma mudanca do banco de producao ou de cota.
- Nenhuma geracao real, nenhum token de API, nenhuma cobranca.
- Nao exibir gabarito antes da curadoria nem aprovar automaticamente.
