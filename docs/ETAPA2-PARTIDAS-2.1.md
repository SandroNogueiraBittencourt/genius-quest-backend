# Etapa 2.1 — Partidas vinculadas ao tema vencedor

## Escopo implementado
- **POST /api/partidas** com JWT, payload `{"cicloId":"uuid"}`: cria partida pessoal no estado `PREPARACAO`. O backend determina o tema vencedor, não aceita `temaId` escolhido pelo cliente. Resposta `201 Created`.
- **GET /api/partidas/minhas** com JWT: até 20 partidas do usuário logado, mais recentes primeiro.
- **GET /api/partidas/{id}** com JWT: somente criador pode consultar; id inexistente/de outra conta retorna 404.
- Migration **V7** cria tabela `partidas` com referências a `ciclos_votacao`, `temas` e `usuarios`.
- A partida guarda instantâneo do título vencedor obtido do ranking encerrado e o nome da categoria na criação, de modo que alterações editoriais posteriores não mudem o contexto.

## Regras
- Somente ciclos encerrados **com vencedor** são elegíveis. Ciclos abertos, sem vencedor, inexistentes => 409/404.
- Usuário deve estar autenticado e ativo. JWT não é suficiente para aceitar IDs de outras contas.
- O vínculo ao vencedor é atribuído **sempre pelo servidor**.
- Uma conta pode preparar várias partidas a partir de um mesmo ciclo; não iniciamos um quiz nem debitamos moedas.
- O lobby do frontend continua uma demonstração separada.

## Próxima subetapa
- 2.2: geração ou curadoria de perguntas baseada no contexto (tema, categoria), com validação estruturada das perguntas, persistência e política de falhas/retries da IA. As chaves de provedores nunca estarão no navegador.
- 2.3: resposta e correção no backend; não enviar gabarito antes da resposta.
- 2.4: pontuação persistida, fim de jogo e resultados.
- 2.5: salas e jogadores reais (não reutilizar `demoGateway`).

## Segurança operacional
- Executar `./mvnw clean verify` com PostgreSQL local de testes, depois validar API e interface no Docker.
- A branch `develop` publica automaticamente no Render; não realizar merge sem backup de produção e aprovação.
