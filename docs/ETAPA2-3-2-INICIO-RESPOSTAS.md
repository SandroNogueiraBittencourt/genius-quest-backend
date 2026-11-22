# GeniusQuest — Etapa 2.3.2: snapshot e respostas da partida

## Objetivo e escopo
Construir a primeira parte do jogo efetivo no backend sem expor gabaritos:
- iniciar uma partida vinculada ao vencedor do ciclo encerrado;
- congelar **no máximo 10 questões APROVADAS**, seus enunciados, alternativas
  e resposta correta na mesma transação;
- aceitar **uma única resposta por pergunta e jogador**, com correção no servidor;
- permitir retomada do progresso sem divulgar a correção antes do resultado final.

Nesta entrega só o **criador da partida** participa como jogador. Não há
multijogador, pontuação final nem encerramento automático. Essas funcionalidades
ficam para as etapas seguintes.

## Migração V13 (PostgreSQL 18)

`V13__perguntas_e_respostas_por_partida.sql` cria:
- `perguntas_partida`: snapshot imutável por partida com `UNIQUE(partida_id, pergunta_id)`
  e `UNIQUE(partida_id, ordem)`, preservando alternativa 0–3 e gabarito interno;
- `respostas_partida`: resposta por usuário com `UNIQUE(pergunta_partida_id, usuario_id)`,
  índice selecionado, correção booleana interna e data;
- `CHECK` para índices 0–3, ordem 0–9 e dificuldade.
Sem alteração de tabelas antigas; sem novos registros de produção até criar/iniciar
partidas. A migration **não pode ser aplicada em produção sem backup e ensaio
de restauração num PostgreSQL isolado**, usando o dump de produção mais recente.

## API

`POST /api/partidas/{id}/iniciar` — JWT do criador
- `200`: `PartidaResponseDTO`, status `EM_ANDAMENTO`;
- `401`: não autenticado; `404`: partida não encontrada ou de outra conta;
- `409`: status diferente de `PREPARACAO` ou nenhum item `APROVADA` no tema vencedor.
- Um lock pessimista em `partidas` serializa duas solicitações de início.
- Salva todo o snapshot + novo status na mesma transação; falhas fazem rollback.

`GET /api/partidas/{id}/perguntas` — JWT do criador
- `PREPARACAO`: apenas prévia dinâmica (contrato da 2.3.1);
- iniciada: dados persistidos de `perguntas_partida` (estáveis mesmo que a
  curadoria edite/bloqueie o original);
- retorno inclui **id da pergunta**, enunciado, quatro alternativas e dificuldade.
- **Nunca retorna** `respostaCorretaIndice` ou correção `correta`.

`POST /api/partidas/{id}/respostas` — JWT do criador; JSON:

```json
{"perguntaId":"UUID", "alternativaIndice": 2}
```

- `201`: `{perguntaId, alternativaIndice, respondidaEm}`.
- `400`: payload inválido / índice fora de 0–3;
- `401`: não autenticado; `404`: partida de outro usuário ou pergunta fora
  do snapshot; `409`: não iniciada ou já respondida.
- A coluna `correta` é computada e persistida, **não enviada ao navegador**.
- Lock em partida + índice único no banco protegem contra submissões repetidas
  ou simultâneas. O cliente não pode escolher o tema vencedor nem fornecer
  o gabarito.

`GET /api/partidas/{id}/respostas` — JWT do criador
- Lista somente as próprias respostas, na ordem de envio;
- não revela acertos nem índices corretos, útil para continuar após refresh.
- Outras contas recebem 404.

## Testes
`PartidaRespostasIntegrationTest` usa MockMvc/PostgreSQL de testes para validar:
- 401 e isolamento 404;
- tentativa de iniciar sem aprovadas e reinício 409;
- snapshot imutável após mudança/bloqueio da candidata original;
- resposta correta e incorreta persistida internamente, sem vazamento no JSON;
- duplicidade 409, pergunta fora da partida 404, índices inválidos 400;
- limite de 10 perguntas;
- status não muda para `CONCLUIDA` automaticamente nesta etapa.

CI: `./mvnw -B clean verify` e build da imagem Docker.

## Planejamento de rollout
1. Revisar diff e CI do PR (sem dados reais/segredos).
2. **Antes de qualquer deploy**: realizar backup atual do Render, verificar
   `pg_restore --list`, restaurar num PostgreSQL 18 efêmero e aplicar o Flyway
   V12→V13 **apenas na cópia** com este utilitário:

   ```bash
   # Executar na raiz da branch deste PR, no seu computador Arch Linux.
   python3 scripts/geniusquest_v13_backup_restore.py backup

   # Use o caminho do .dump recém-criado, informado pelo script anterior.
   python3 scripts/geniusquest_v13_backup_restore.py restore-test \\
     --backup "$HOME/Backups/geniusquest/render/geniusquest_pre_v13_AAAAmmddTHHMMSSZ.dump"
   ```

   A senha da conexão é solicitada sem eco, o dump e manifesto SHA-256 ficam
   no diretório privado `~/Backups/geniusquest/render`. O teste exige a fonte
   Flyway V12 e confere o histórico e os fingerprints de **todas as tabelas
   preexistentes**, inclusive cotas e perguntas de IA, após aplicar somente a V13.
   Rede Docker interna, sem portas no host, com limpeza automática ao terminar.
   **Nunca compartilhar URL do banco, dump, hashes de senhas ou chaves em chats.**
   Validar preservação de dados e migrations sem falhas.
3. Autorizar explicitamente deploy que aplicará V13 no PostgreSQL de produção.
4. Validar V13 sem inserir dados de teste artificialmente nos ciclos encerrados.
   Produção tem uma pergunta aprovada em Animais Pré-Históricos, mas os ciclos
   anteriores têm Animais Selvagens como vencedor e 0 perguntas aprovadas;
   iniciar uma partida desses ciclos deve retornar HTTP 409.
5. Após homologação, implementar Etapa 2.4 (encerramento, pontuação e
   divulgação do feedback final), depois interface jogável.

## Riscos e limites
- O snapshot não muda se uma pergunta original for bloqueada após o início.
  Definir uma política explícita de cancelamento ou invalidação de partidas
  quando ocorrer remoção por erro de conteúdo/segurança.
- O status `EM_ANDAMENTO` permanece até a entrega de finalização; não há
  limite de tempo, nem ranking ou moedas.
- Apenas um participante (criador); multijogador requer modelagem distinta.
- As flags `APP_IA_ENABLED` e `APP_IA_HTTP_ENABLED` permanecem `false`.
- Não há chamadas pagas à OpenAI nesta etapa.
