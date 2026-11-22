# Etapa 2.2 — Backup real, restauração isolada e ensaio da V12

## Estado conhecido (22/11/2026)

A inspeção **somente leitura** no Render identificou `genius-quest-db`, PostgreSQL 18,
com Flyway até **V11** e zero migrations com falha. O backend publicado está em
`e66c8df`; `develop` já contém a V12. O frontend publicado ainda não contém a tela IA.
Ambos os serviços Render têm **deploy automático desativado**.

O workflow `Flyway V12 - Upgrade Isolado` do PR #31 passou em PostgreSQL 18
com **dados sintéticos**. Esta etapa tem objetivo diferente: restaurar uma **cópia
atualizada do banco verdadeiro** e ensaiar nela a V12 antes do deploy.

## Pré-requisitos

- Arch Linux com PostgreSQL client `pg_dump`, `pg_restore` e `psql` da versão
  **18 ou superior compatível**, Python 3 e Docker funcionando.
- Repositório backend atualizado até a branch deste PR, com as migrations V1-V12.
- **External Database URL** do Render, obtida no Dashboard (não cole em chats,
  scripts, comandos, arquivos de configuração ou GitHub).
- Espaço local livre para o dump e sua restauração temporária (o banco inspecionado
  tinha aproximadamente 8,7 MB no instante da consulta, mas pode crescer).

## 1 — Exportar o dump (somente leitura na origem)

A partir da raiz do repositório backend:

```bash
python3 scripts/geniusquest_v12_backup_restore.py backup
```

O programa pedirá a External Database URL **sem ecoar a senha**. Ele verifica a
última versão Flyway, executa `pg_dump --format=custom --no-owner
--no-privileges` com conexão SSL obrigatória, verifica `pg_restore --list` e cria:

- `~/Backups/geniusquest/render/geniusquest_pre_v12_<UTC>.dump`
- o mesmo caminho acrescido de `.sha256`

A pasta fica com permissão 0700; os arquivos, 0600. Não subir esses arquivos
para o repositório nem enviá-los por chat, pois contêm dados pessoais e hashes.

**Não compartilhe a URL de conexão.** O programa a recebe por `getpass`, repassa
a senha somente ao processo PostgreSQL por variável de ambiente e nunca a
coloca em argumentos visíveis de linha de comando.

## 2 — Validar em PostgreSQL 18 temporário

Substitua o nome do dump abaixo pelo caminho retornado no passo anterior:

```bash
python3 scripts/geniusquest_v12_backup_restore.py restore-test \
  --backup "$HOME/Backups/geniusquest/render/geniusquest_pre_v12_AAAAmmddTHHMMSSZ.dump"
```

O script verifica o SHA-256 **antes** de criar contêineres. Em seguida:

1. Cria rede Docker **interna**, sem acesso externo e sem publicar portas.
2. Inicia PostgreSQL 18 efêmero, em contêiner de nome exclusivo.
3. Restaura o dump com `pg_restore --exit-on-error`.
4. Confirma que a origem restaurada estava em V11.
5. Produz fingerprints e contagens de **todas as tabelas preexistentes**,
   preservando também o histórico Flyway V1-V11.
6. Executa o Flyway **somente até V12** nessa cópia restaurada.
7. Verifica V12 aplicada uma vez, dados antigos intactos, histórico Flyway
   preservado e tabelas IA novas sem registros.
8. Remove contêiner, volumes efêmeros e rede em bloco `finally`, inclusive
   se alguma etapa falhar. O dump local permanece para recuperação.

O teste usa a imagem oficial `postgres:18` e `flyway/flyway:11`. Não compartilha
rede, arquivos de banco nem credenciais com a produção. **Não executa nenhum
SQL de escrita contra o Render.**

## Resultado do ensaio com backup real (22/11/2026)

**Evidência fornecida pelo operador:** execução do utilitário no Arch Linux, com
exportação de `pg_dump` no formato custom, manifesto SHA-256 e
`restore-test` em banco PostgreSQL 18 temporário, usando cópia do Render.

O log apresentado confirma:
- `pg_restore` concluído na instância Docker isolada;
- versão do histórico antes do teste: **V11**;
- Flyway OSS 11.20.3 validou 12 migrations;
- Flyway aplicou **somente a V12** sobre a cópia;
- verificação de integridade: `OK: dados preexistentes, historico Flyway e V12 validados`;
- limpeza do contêiner e rede temporários;
- `SUCESSO: backup restaurado e V11 -> V12 validado em isolamento Docker.`

Não foram compartilhados dump, URL de conexão nem segredos. O log informado
comprova o ensaio concluído na máquina do operador; não constitui uma
execução automática do CI contra o banco real. A V12 permanece não aplicada
em produção até autorização específica do deploy.

## 3 — Critérios de liberação

- [x] Backup novo exportado, `pg_restore --list` válido e SHA-256 conferido.
- [x] `restore-test` terminou com mensagem **SUCESSO**.
- [x] Histórico Flyway V1-V11 e fingerprints das tabelas antigas íntegros.
- [x] Nenhum segredo, dump ou dado pessoal foi commitado.
- [ ] `Backend CI` aprovado no `develop` após integrações relevantes.
- [ ] Autorizar **separadamente** o deploy do backend com V12. Os flags
      `APP_IA_ENABLED=false` e `APP_IA_HTTP_ENABLED=false` devem permanecer off.
- [ ] Homologar GET administrativo, 401/403, erro 503 com IA off e curadoria.
- [ ] Só depois configurar alertas/limites de cobrança e autorizar um teste real.

## O que este procedimento não faz

Não executa `pg_restore` na origem, não realiza rollback em Render, não
faz deploy, não altera variáveis de ambiente e não chama a API da OpenAI.

O Render oferece recuperação para um ponto no tempo (PITR) em bancos pagos,
mas a exportação lógica testada constitui uma camada independente. Antes de
qualquer operação de recuperação em produção, consulte a documentação oficial
para evitar substituição de instância ou perda de dados recentes.