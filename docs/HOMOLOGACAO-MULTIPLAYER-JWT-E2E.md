# Homologacao multiplayer end-to-end (JWT real / Docker isolado)

## Abrangencia

A classe `SalaMultiplayerJwtFluxoCompletoIntegrationTest` realiza o fluxo
completo com HTTP MockMvc e **JWTs assinados gerados por login real** para
tres contas ficticias (anfitriao, convidado e intruso).

1. O anfitriao cria a sala e o convidado entra sem duplicar participante.
2. A votacao de tema e aberta pelo anfitriao, ambos votam, e somente
   o anfitriao escolhe o tema vencedor.
3. O inicio exige o anfitriao e 10 perguntas aprovadas; a API nao
   expoe o gabarito nem para o convidado.
4. Os participantes registram 20 respostas no total; respostas duplicadas
   e acesso de intruso nao autenticado/sem ingresso sao bloqueados.
5. O placar final e disponibilizado somente apos a conclusao: o anfitriao
   soma 200 pontos em dez questoes medias, o convidado soma zero, e as duas
   classificacoes sao conferidas no banco e na resposta da API.
6. O convidado encontra a partida em "Minhas partidas", com seu codigo de
   sala. Os jogadores registrados preservam o acesso ao placar historico.
7. Os dois votam por continuar; a nova votacao fica sem votos anteriores.
   O placar da rodada concluida permanece igual apos a continuacao e o
   encerramento da sala, sem depender de seu estado atual.
8. Um segundo teste verifica isolamento do lobby e rotas sem JWT.

## Isolamento

`@ActiveProfiles("test")` usa o PostgreSQL 18 do job **Backend CI**
no GitHub Actions (`geniusquest_test`); toda a fixture e sintetica e
os testes usam `@Transactional` para rollback. Nao ha chamadas HTTP
externas, tokens reais do usuario, envio de emails ou custos de IA.
Este teste NAO aponta para o Render. Nao requer deploy para executa-lo.

## Como validar

Verificar que `Backend CI` conclui `./mvnw -B clean verify` com sucesso
e valida o build Docker. Este cenario nao substitui testes de concorrencia
entre conexoes simultaneas nem teste E2E com dois navegadores reais.
