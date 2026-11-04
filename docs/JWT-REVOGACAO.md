# Genius Quest — Etapa 0.2: revogação de JWT

## Contrato de API

- `POST /api/auth/login`: recebe `email` e `senha`, retorna `token`, `tipo` e `expiraEm`.
- `POST /api/auth/logout`: exige `Authorization: Bearer <JWT>`, não recebe corpo e responde **204 No Content**.
- Solicitações posteriores com o mesmo JWT revogado retornam **401 Unauthorized**.
- Um JWT ausente, inválido, expirado ou anteriormente revogado não pode realizar logout e recebe **401**.
- O logout encerra **somente o token informado**, não todas as sessões do usuário. Outra sessão com JWT distinto permanece válida.

## Implementação

- Cada novo JWT contém um identificador aleatório (`jti`) para impedir colisões entre logins realizados no mesmo segundo.
- A migration Flyway `V4` cria `jwt_tokens_revogados` com hash SHA-256 do JWT e respectiva expiração; o token original nunca é persistido.
- `TokenRevocationService` registra a revogação no PostgreSQL e `JwtAuthenticationFilter` consulta a tabela a cada token válido.
- As instâncias que utilizam o mesmo PostgreSQL e a mesma chave JWT compartilham imediatamente a lista de revogação.
- O insert é idempotente (`ON CONFLICT DO NOTHING`). Uma tarefa agendada diariamente às **03:00 UTC** exclui registros de tokens expirados.
- Se o banco de dados estiver indisponível, a autenticação dos JWTs também falha; não há fallback que ignore revogações.
- Requisições que já passaram pelo filtro antes do logout podem terminar normalmente. O bloqueio vale para novas solicitações após a revogação ser confirmada.

## Integração com frontend

Após logout confirmado, o frontend deve descartar o JWT e o estado de sessão local. **Não registrar JWTs em logs** ou mensagens de erro. Tratar o retorno 401 limpando a sessão local.

A implantação exige aplicar a migration `V4` antes que novas instâncias atendam chamadas JWT, pois o filtro consulta a tabela de revogação.

## Testes

```bash
docker start geniusquest-test-db
cd ~/Documentos/ADS/Projeto-fullstack/genius-quest-backend
env -u DB_PASSWORD -u SPRING_DATASOURCE_PASSWORD -u SPRING_DATASOURCE_URL ./mvnw clean verify
```

A configuração de testes utiliza `geniusquest_test` em `127.0.0.1:5433` e importa `.env` via Spring Config.
