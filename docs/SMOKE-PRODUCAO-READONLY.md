# Homologação HTTP de produção — GeniusQuest

O smoke test `scripts/smoke_producao_readonly.py` exercita somente GET e
OPTIONS contra o backend e frontend publicados no Render, sem credenciais e
sem alterar registros, sem criar partidas, sem solicitar email e sem chamar
geração de perguntas por IA.

Valida:
- `/api/health` → HTTP 200, `status=UP`
- `/api/categorias?page=0&size=5` → HTTP 401 sem JWT
- `/api/temas` → HTTP 401 sem JWT
- `/api/partidas/minhas` → HTTP 401 sem JWT
- `/api/admin/perguntas/geracao/status` → HTTP 401 sem JWT
- Swagger UI e OpenAPI (`/swagger-ui.html`, `/swagger-ui/index.html`, `/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/swagger-config`) → HTTP 401 sem JWT; nenhuma definição de API pode ser publicada anonimamente
- OPTIONS `/api/temas` com Origin do frontend → CORS permitido
- frontend estático → HTTP 200 com conteúdo HTML

O teste não valida login real, permissões 403 com usuário comum,
listagem autenticada ou fluxos de escrita. Essas verificações exigem
credenciais e devem ser feitas separadamente, sem compartilhar tokens.

Execução no GitHub Actions: PR que modifica este utilitário e push para
`develop` nos arquivos especificados; não há secrets nesse job. A ação
`workflow_dispatch` só fica disponível no GitHub se o workflow estiver na
branch padrão (`main`), que atualmente difere da branch de deploy
(`develop`).
