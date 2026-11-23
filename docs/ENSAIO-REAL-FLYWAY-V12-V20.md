# Ensaio do backup real — Flyway V12 → V20

## Escopo e limite de segurança
- O backup aceita **somente origem Flyway V12**, comprovada por SELECT no banco.
- A origem Render recebe apenas SELECT e pg_dump; nenhuma migration ou restore ocorre em produção.
- Dump em formato custom, SHA-256 e manifesto com permissão privada.
- O ensaio restaura a cópia em PostgreSQL 18 num Docker **interno sem portas publicadas**, com volume efêmero.
- Aplica exclusivamente migrations V13–V20 à cópia via Flyway 11.
- Compara fingerprints e contagens de todas as tabelas antigas, histórico Flyway V1–V12
  e objetos introduzidos pelas migrations V13–V20.
- A coluna `partidas.pergunta_atual`, acrescentada na V18 com DEFAULT 1, é excluída
  **apenas da comparação da projeção original** de `partidas`. Todas as demais
  colunas antigas continuam verificadas.
- Nenhuma configuração da OpenAI ou da Brevo é alterada.
- O ensaio não substitui backup periódico nem autorização explícita de deploy.

## Uso no Arch Linux
Execute na raiz do repositório, após `git switch develop && git pull --ff-only origin develop`:

```bash
umask 077
python3 scripts/geniusquest_v20_backup_restore.py backup
# Digite a External Database URL do Render no prompt mascarado, nunca cole no chat.

# Substitua o nome pelo arquivo efetivamente gerado no passo anterior:
python3 scripts/geniusquest_v20_backup_restore.py restore-test \
  --backup "$HOME/Backups/geniusquest/render/geniusquest_pre_v20_AAAAMMDDTHHMMSSZ.dump"
```

Critérios para prosseguir: validação do hash, restore `pg_restore`, Flyway V20
sem falha, preservação de todas as tabelas antigas e mensagem final `SUCESSO`.

Caso o banco já esteja em V13+ ou V20, **não** adapte os checks por conta
própria; interrompa e revise o estado e o plano de migração antes de publicar.

Não envie URL do banco, dump, logs com credenciais nem registros sensíveis
para o GitHub ou para o chat.
