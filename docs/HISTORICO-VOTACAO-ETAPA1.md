# Genius Quest — Histórico de votações

## Endpoints

- `GET /api/ciclos-votacao/historico?page=0&size=10`: acessível com JWT; lista **somente** ciclos `ENCERRADO`, em ordem decrescente de encerramento, com paginação (size 1–50).
- `GET /api/ciclos-votacao/{id}/ranking`: quando o ciclo está encerrado, retorna os resultados congelados; quando está aberto, retorna a contagem atual. Requer JWT.
- `GET /api/ciclos-votacao/atual`: segue retornando apenas ciclo ativo.

Resposta de histórico:
```json
{
  "ciclos": [{
    "id": "uuid", "nome": "Votação de Novembro",
    "inicioEm": "2026-11-08T17:00:00Z",
    "fimEm": "2026-11-08T18:00:00Z",
    "encerradoEm": "2026-11-08T17:09:00Z",
    "vencedorTemaId": "uuid", "vencedorTitulo": "História do Futebol",
    "totalVotos": 2, "rankingReconstruido": true
  }],
  "pagina": 0, "tamanho": 10, "total": 1, "temProxima": false
}
```

## Persistência

A migration `V6__historico_resultados_votacao.sql` cria `resultados_ciclo_votacao`.
Ao encerrar um ciclo, a operação grava a classificação e os títulos como fotografia atômica, incluindo temas com zero votos que sejam elegíveis no encerramento. A posição continua obedecendo votos DESC, data de criação ASC e UUID ASC.

**Ciclos encerrados antes da V6:** a migration reconstrói os votos por tema para os ciclos já encerrados. Não é possível recuperar a elegibilidade histórica nem temas com zero votos na época. Esses ciclos são identificados por `rankingReconstruido=true` para não apresentar um ranking reconstruído como se fosse original.

A lista não expõe identidades de votantes. O total é obtido diretamente da tabela `votos_tema`; para empates, o vencedor registrado no próprio ciclo permanece a fonte da verdade.

## Validações
- [ ] `SPRING_DOCKER_COMPOSE_ENABLED=false ./mvnw clean verify` no banco de testes PostgreSQL 18
- [ ] Fluxo manual com JWT local
- [ ] Backup e revisão da migration antes de merge em `develop` (deploy Render automático)
