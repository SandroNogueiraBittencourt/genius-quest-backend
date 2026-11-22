# GeniusQuest — Etapa 2.3.1: consulta segura de perguntas elegíveis

## Situação inicial, 22/11/2026

- A primeira pergunta `9e5681d6-f20a-4d36-9479-23877a8ac38a` foi aprovada pelo ADMIN,
  vinculada ao tema aprovado `63b3eac4-a267-4f53-9177-ddd63b65410a`
  (Animais Pré-Históricos), com fonte científica e histórico REVISAR → APROVAR.
- O PostgreSQL de produção possui **1 pergunta aprovada** e **0 partidas**.
- Portanto, não há partida real de produção na qual observar a seleção. Este PR
  testa o contrato em banco isolado via MockMvc, sem criar partidas de homologação
  em produção.
- Frontend `QuizPreparation` atual ainda informa que a partida não é jogável;
  não atualizar essa mensagem até que o ciclo de respostas e pontuação exista.

## Contrato novo (somente leitura)

`GET /api/partidas/{id}/perguntas` com JWT, exclusivo do criador da partida.

Resposta `200 OK`, lista ordenada (criação e ID), no máximo 10 questões
`APROVADA` do **tema vencedor vinculado pelo servidor à partida**:

```json
[
  {
    "id": "UUID_DA_PERGUNTA",
    "enunciado": "Qual animal era um réptil marinho?",
    "alternativas": ["Mosassauro", "Tricerátops", "Tiranossauro", "Estegossauro"],
    "dificuldadeInicial": "MEDIA"
  }
]
```

- Nenhuma pergunta aprovada => `[]`. Isso não cria uma partida nem a inicia.
- Não autenticado => 401; outra conta ou partida inexistente => 404,
  sem revelar a existência de partidas particulares.
- **Nunca expor:** `respostaCorretaIndice`, fonte, identificador do revisor,
  snapshots, eventos de curadoria, modelo de geração ou outros dados de moderação.
- Não aceitar IDs de tema/pergunta escolhidos pelo cliente. O servidor resolve
  o tema da própria partida.
- A lista é **prévia dinâmica somente leitura**, não um sorteio persistido:
  não congela questões e não vincula respostas ao jogador. Alterações de
  curadoria podem mudar o conjunto retornado até o lançamento do quiz.

## Testes

`PartidaPerguntasElegiveisIntegrationTest` verifica: 401, 404 entre contas,
somente aprovadas do tema vencedor, exclusão de pendentes/rejeitadas/bloqueadas,
ausência do gabarito/dados ADMIN, resposta vazia e limite de 10 questões.

Executar no CI existente `./mvnw -B clean verify` com PostgreSQL 18,
seguido pelo build Docker.

## Próxima entrega: Etapa 2.3.2

Implementar **vínculo persistido e imutável** de um conjunto de perguntas
por partida, recebimento de respostas e apuração no backend. Exigir:
- perguntas efetivamente selecionadas e aprovadas antes de iniciar;
- vínculo de jogador/partida e proteção contra respostas de outras contas;
- uma resposta por pergunta (idempotência/concorrência), sem correção pelo frontend;
- gabarito oculto enquanto o jogador ainda não respondeu e sem endpoints públicos
  que antecipem o resultado;
- revisão da política de bloqueio de perguntas durante partidas em andamento;
- testes de 401/403/404/409, SQL/Flyway em banco isolado e rollback;
- pontuação/finalização na subetapa 2.4, sem prometer quiz jogável até esse marco.

## Segurança operacional

- **Nenhuma nova migration**, nenhum deploy, nenhuma chamada à OpenAI, nenhuma
  alteração nas flags ou credenciais.
- A aplicação no Render requer CI e autorização separados; ambas as flags
  `APP_IA_ENABLED` e `APP_IA_HTTP_ENABLED` devem permanecer `false`.
