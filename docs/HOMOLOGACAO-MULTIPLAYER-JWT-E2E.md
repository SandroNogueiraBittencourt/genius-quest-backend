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
5. A partida termina apos 10 perguntas, os dois votam por continuar,
   a votacao do proximo tema e reaberta, e o anfitriao fecha a sala.
6. Um segundo teste verifica isolamento do lobby e rotas sem JWT.

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
