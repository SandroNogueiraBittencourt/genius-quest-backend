# Etapa 2.2 — Perguntas, IA e curadoria

## Subetapa 2.2.1 — Modelo de dados e validação (este commit)

- `V8__create_perguntas_alternativas.sql`: perguntas candidatas e quatro alternativas.
- Campos para origem (`IA`/`MANUAL`), modelo, versão, fonte, dificuldade inicial,
  revisor, motivo e data de revisão.
- Estado inicial `PENDENTE`; aprovação administrativa futura é necessária antes
  de utilizar uma pergunta como conteúdo oficial.
- Índice único por tema + enunciado normalizado para candidatas não rejeitadas.
- Validação determinística: enunciado não vazio, exatamente 4 alternativas distintas,
  índice da resposta correta válido e limites de comprimento.
- Gabarito fica exclusivamente no backend; ainda não existe rota pública para perguntas.

## Próximas subetapas

1. **2.2.2 — Provedor de IA:** adaptador configurável no servidor, saída estruturada,
   limites de lote, timeout, retries controlados, rate limit, custo e segredos via env.
   Respostas são *candidatas* e não podem ser publicadas automaticamente.
2. **2.2.3 — Curadoria ADMIN:** endpoints protegidos para registrar, listar, editar,
   aprovar, rejeitar e bloquear perguntas; auditoria de autor/revisor e testes 401/403.
3. **2.2.4 — Integração:** testes completos, contratos de API, tela administrativa,
   preparação de partida com 10 perguntas aprovadas (sem expor gabarito).

## Restrições de segurança

- Nunca enviar a resposta correta em payloads destinados ao jogador.
- Não confiar em conteúdo de IA sem validação estrutural **e** revisão de conteúdo.
- Evitar duplicidade por normalização/índices e checagem de conteúdo.
- Não habilitar chamadas pagas do provedor ou executar migrations de produção
  antes de testes locais, revisão e autorização de merge em `develop`.
- Migrar somente em PostgreSQL isolado (profile `test`) nesta fase.
