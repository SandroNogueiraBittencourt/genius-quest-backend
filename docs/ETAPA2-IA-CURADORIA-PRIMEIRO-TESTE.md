# Etapa 2.2.9 — Homologação da curadoria da primeira pergunta IA

Data: 22/11/2026.

## Evidências em produção (somente leitura)

- Backend Render: commit `14b8297658bcaa61a43bedf3b841bc887be5c421` (`live`).
- Flyway PostgreSQL: **V12**, sem falha de migration.
- Primeira solicitação IA: `79a7e0a3-43bb-4f84-8d8d-5108de42f458`,
  quantidade **1**, status `CONCLUIDA`.
- Pergunta: `9e5681d6-f20a-4d36-9479-23877a8ac38a`,
  `origem=IA`, `status=PENDENTE`, `modelo_ia=gpt-6-luna`,
  dificuldade `MEDIA`; inicialmente sem revisor, revisão ou fonte registrada.
- Tema: `Animais Pré-Históricos`, categoria `Animais`.
- Quota: 1 solicitação, 1 pergunta (dia UTC de 22/11).
- O Render recebeu atualização para `APP_IA_ENABLED=false` e
  `APP_IA_HTTP_ENABLED=false`; geração ficou desativada.
- **Custo OpenAI confirmado em Analytics → Gastos:** **US$ 0,000067**
  para a geração, com 229 tokens de entrada, 89 de saída (318 no total).
  A atualização dos indicadores de uso ocorreu depois do primeiro painel zerado.
  O orçamento do projeto era de US$ 1 com limite aplicado.

## Referência para curadoria

Enunciado: `Qual destes animais pré-históricos era um réptil marinho, e não um dinossauro?`

Resposta: **Mosassauro** (posição 3 na interface, índice 2 na API).

Fonte pública relevante, verificada em 22/11/2026:
**Natural History Museum, London — What are dinosaurs?**
https://www.nhm.ac.uk/discover/what-are-dinosaurs.html

O museu distingue mosassauros e outros répteis marinhos do grupo dos dinossauros.
Esta fonte foi selecionada para orientar a revisão humana; ainda **não foi
gravada na pergunta de produção**.

## Proteção proposta neste PR

O backend passa a recusar `APROVAR` para candidatas `origem=IA`
com `fonteReferencia` nula ou vazia (HTTP 409).
A pergunta continua `PENDENTE` e sem novos eventos de curadoria.
A administração pode preencher a fonte por
`PATCH /api/admin/perguntas/{id}/revisar` antes de aprovar.
A origem `MANUAL` mantém o contrato atual, sem exigência adicional.

Isso **não valida automaticamente a qualidade da fonte**: o administrador deve
ler a referência e verificar a resposta antes de publicar.

## Procedimento no navegador

1. Abrir `https://genius-quest-web.onrender.com/?view=questions-admin`
   autenticado como ADMIN, selecionar a pergunta pendente.
2. Clicar em **Editar conteúdo** e conservar o enunciado, alternativas
   e gabarito após conferência.
3. Preencher `fonteReferencia` com o endereço do Natural History Museum acima.
4. Registrar justificativa de revisão, por exemplo:
   `Referência verificada; mosassauro é réptil marinho e não dinossauro.`
5. Salvar a revisão; confirmar que continua `PENDENTE` e que o histórico
   contém `REVISAR`.
6. **A aprovação final é decisão humana separada**. Caso o administrador a
   aprove, confirmar status `APROVADA` e evento `APROVAR` no histórico.
7. Se houver dúvida sobre conteúdo ou qualidade, rejeitar ou bloquear
   conforme a política de curadoria. Não aprovar automaticamente.

## Segurança e escopo

- Este PR **não realiza mutações em produção**, migrações ou requisições à OpenAI.
- Os testes usam Spring MockMvc/PostgreSQL de testes sem chamadas pagas.
- Não há alteração de quotas, credenciais, perfis nem flags.
- **Não implantar o backend sem a validação do CI e revisão do PR**.

## Atualização da curadoria após o primeiro teste

Em 22/11/2026 às 20:48 UTC, o administrador usou a tela de moderação
para revisar a candidata `9e5681d6-f20a-4d36-9479-23877a8ac38a`:

- `status=PENDENTE` foi preservado, sem aprovação automática.
- `fonteReferencia` foi registrada como
  `https://www.nhm.ac.uk/discover/what-are-dinosaurs.html?utm_source=chatgpt.com`.
  O parâmetro `utm_source` é dispensável; a referência do museu permanece válida.
- O histórico passou a conter um evento `REVISAR`, com revisor e motivo:
  `Referência científica conferida. O mosassauro era um réptil marinho, não um dinossauro.`
- A revisão e o histórico foram conferidos via SQL somente leitura no banco Render.

A conclusão desta atualização não significa aprovação. A decisão `APROVAR`
continua dependente do administrador após revisão do conteúdo.
