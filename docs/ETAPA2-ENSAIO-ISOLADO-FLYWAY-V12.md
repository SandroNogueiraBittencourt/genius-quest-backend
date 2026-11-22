# Etapa 2 — ensaio isolado Flyway V11 → V12

## Resultado do ensaio sintético

Fluxo reproduzível no GitHub Actions, rodando exclusivamente no runner:

1. Criar PostgreSQL 18 temporário, database `geniusquest_v12_lab`, sem credenciais do Render.
2. Executar Flyway OSS 11, migrar somente até **V11**.
3. Inserir usuário, categoria, tema, pergunta manual bloqueada, quatro alternativas e evento de curadoria **fictícios**.
4. Registrar snapshots (quantidade e fingerprints MD5) de todas as tabelas de V11 e das linhas antigas de `flyway_schema_history`.
5. Executar a migration V12 pelo Flyway real.
6. Verificar V12 aplicada uma vez, todas as migrations anteriores preservadas, nenhuma migration falhou, dados e snapshots preexistentes idênticos, novas tabelas e índice presentes e vazios.

**Execução aprovada:** [Workflow #45028637439](https://github.com/SandroNogueiraBittencourt/genius-quest-backend/actions/runs/45028637439), commit `7de220920da253ee310a5dd0e5af3cab111f3eb8`.

O script do ensaio é `scripts/ci_ensaio_flyway_v11_v12.sh` e o workflow é `.github/workflows/ensaio-flyway-v12.yml`.

## Limite da evidência

Este ensaio demonstra **compatibilidade técnica da migration com dados sintéticos em PostgreSQL 18**. Não comprova a restauração do banco real do Render nem substitui o backup. Todos os dados do banco de testes do CI são descartados ao final.

## Backup e restauração com dados reais

Fora do GitHub CI, os scripts **locais** `geniusquest_backup_pre_v12.py` e `geniusquest_ensaio_isolado_v12.py` (fornecidos ao operador em pacote separado) geram o `pg_dump` no Arch Linux, verificam o SHA-256 e restauram em Docker isolado. Eles não devem receber segredos por argumentos de shell ou colocar dumps no repositório.

## Segurança do deploy

- Backend Render permanece em `e66c8df`, Flyway produção V11;
- O merge backend `develop` está em `6df6675`, com V12 **não publicada**;
- `APP_IA_ENABLED=false` e `APP_IA_HTTP_ENABLED=false` por padrão;
- Não realizar deploy, aplicar V12 em produção, ativar IA, sincronizar Blueprint ou publicar frontend sem autorização explícita;
- Obter **backup real verificado** antes da autorização de deploy.
