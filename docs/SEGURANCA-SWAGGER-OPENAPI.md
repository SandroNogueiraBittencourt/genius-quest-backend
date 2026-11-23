# Restringir Swagger/OpenAPI no GeniusQuest

## Objetivo

No backend, `/api/health`, login, recuperação de senha e os demais endpoints
mantêm suas regras existentes. A documentação interativa deixa de ser pública.

## Configuração

- `springdoc.api-docs.enabled=${APP_OPENAPI_ENABLED:false}`
- `springdoc.swagger-ui.enabled=${APP_OPENAPI_ENABLED:false}`
- Em `render.yaml`: `APP_OPENAPI_ENABLED=false`
- `SecurityConfig`: rotas `/v3/api-docs`, `/v3/api-docs/**`,
  `/v3/api-docs.yaml`, `/swagger-ui.html`, `/swagger-ui/**`
  exigem `ROLE_ADMIN`.

No ambiente local, ative **somente quando necessário**, exportando
`APP_OPENAPI_ENABLED=true` ao iniciar a aplicação. Mesmo assim, a
documentação exige token de administrador; a UI do navegador não é
automaticamente autenticada. Mantenha a chave desligada no Render.

## Validação

Antes do deploy: Maven CI e testes `SecurityConfigTest` com PostgreSQL
de testes isolado. Esperado com configuração padrão: 401 sem autenticação,
403 com `ROLE_USUARIO`, 404 para administrador autenticado porque a
documentação está desabilitada.

Depois de novo deploy **explicitamente autorizado**: teste HTTP sem credenciais
deve observar HTTP 401 em `/v3/api-docs` e `/swagger-ui.html`, e a API
deve preservar `/api/health` com HTTP 200. Se os endpoints estiverem
desabilitados mas houver retorno 401, isso é a barreira de autenticação
atuando antes do despacho HTTP; não indica exposição da documentação.

Não compartilhe JWT, banco, senha, cabeçalhos Authorization ou valores
de segredos em logs de testes públicos. Esta alteração não executa
migrations nem altera dados em produção.
