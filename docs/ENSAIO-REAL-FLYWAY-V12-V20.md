# Ensaio do backup real — Flyway V12/V13 → V20

## Escopo e limite de segurança
- O backup aceita **somente origem Flyway V12 ou V13**, comprovada por SELECT no banco.
- A origem Render recebe apenas SELECT e pg_dump; nenhuma migration ou restore ocorre em produção.
- Dump em formato custom, SHA-256 e manifesto com permissão privada.
- O ensaio restaura a cópia em PostgreSQL 18 num Docker **interno sem portas publicadas**, com volume efêmero.
- Aplica exclusivamente migrations V13–V20 (origem V12) ou V14–V20 (origem V13) à cópia via Flyway 11.
- Compara fingerprints e contagens de todas as tabelas antigas, incluindo snapshots e respostas V13 quando existentes, histórico Flyway de origem
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

Caso o banco esteja em V14 ou superior, interrompa: esta versão do ensaio aceita apenas origem V12 ou V13.

O banco foi encontrado em V13 em 23/11/2026. O script valida a versão na origem e novamente após o restore, executa apenas a sequência de migrations pendentes, e compara o histórico e os dados antigos. Para V13, são esperadas exatamente sete migrations adicionais (V14–V20).

A revisão também corrige um erro na versão anterior da consulta SQL de verificação: o bloco PostgreSQL passa a usar os delimitadores `DO $$` e `END $$`. A validação é executada com PostgreSQL 18 em CI usando somente dados sintéticos.

Não envie URL do banco, dump, logs com credenciais nem registros sensíveis
para o GitHub ou para o chat.
