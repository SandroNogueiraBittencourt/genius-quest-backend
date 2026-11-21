# Envio de códigos de recuperação pela API HTTPS da Brevo

## Objetivo e implementação atual

O Genius Quest usa a API HTTPS da Brevo (porta 443) no backend hospedado no Render Free, evitando depender de portas SMTP bloqueadas nesse ambiente.

- Endpoint de envio: `POST https://api.brevo.com/v3/smtp/email`.
- Provedor de envio: `MAIL_PROVIDER=brevo-api` no Render; SMTP continua disponível para testes locais com Mailpit.
- O fluxo atual utiliza **código manual de 16 caracteres**, gerado aleatoriamente, com validade de **30 minutos** e **uso único**.
- A aplicação armazena o **hash SHA-256** do código no PostgreSQL; o código em claro aparece apenas na mensagem enviada ao destinatário.
- O e-mail contém o código e um botão para a **página** de redefinição. O código **não é incluído no link**, na query string, no fragmento nem no path.

## Variáveis do backend no Render

| Variável | Valor ou orientação |
| --- | --- |
| `MAIL_PROVIDER` | `brevo-api` |
| `BREVO_API_KEY` | Chave de **API** da Brevo; não é a chave SMTP. Manter somente nas variáveis protegidas do Render. |
| `MAIL_FROM` | Endereço remetente validado na Brevo |
| `FRONTEND_URL` | `https://genius-quest-web.onrender.com` |
| `APP_TRUST_CLOUDFLARE_IP` | Manter `false` até validar que `CF-Connecting-IP` é definido por um proxy confiável e não pode ser falsificado pelo cliente |

**Não** registrar, publicar ou compartilhar `BREVO_API_KEY`, senhas, códigos recebidos por e-mail ou URL de conexão com credenciais. O valor de `MAIL_FROM` deve corresponder ao remetente autorizado na conta Brevo.

Alterações de variáveis de ambiente podem reiniciar ou redeployar o serviço: coordenar essa configuração com o deploy controlado. Não atualizar `DB_USER` ou `DB_PASSWORD` só para habilitar e-mail.

## Fluxo de recuperação

1. O usuário solicita a recuperação em `POST /api/auth/esqueci-senha` informando o e-mail.
2. A API responde de forma genérica para não revelar se a conta existe.
3. Se a solicitação for permitida e a conta estiver ativa, o backend gera o código, grava somente seu hash e agenda o envio após o commit da transação.
4. O usuário recebe o código e acessa `https://genius-quest-web.onrender.com/redefinir-senha`, **sem código na URL**.
5. O formulário envia `POST /api/auth/redefinir-senha` com campos `token` (o código digitado) e `novaSenha` no **corpo JSON** da requisição.
6. O backend valida expiração, limite de tentativas e uso único; após sucesso, atualiza a senha e incrementa `versao_credenciais`, invalidando JWTs anteriores.

As migrações Flyway V10–V11 criam as estruturas de recuperação e rate limiting; V8–V9 pertencem à funcionalidade de perguntas/curadoria da mesma integração.

## Proteção de links e privacidade

O botão do e-mail aponta **somente** para a página de redefinição, sem segredo incorporado. Caso a Brevo reescreva o link para rastrear cliques, isso **não divulga um código de recuperação pela URL**, ao contrário do fluxo legado.

Ainda assim, revisar/desabilitar rastreamento de cliques quando possível por motivos de privacidade e minimizar redirecionamentos. O atributo `contactPixelTrackingConsent=false` não comprova, isoladamente, que o rastreamento está desativado para a conta.

Não compartilhar capturas que mostrem o código real recebido, credenciais ou dados pessoais.

## Validação local (sem tocar em produção)

Executar contra banco PostgreSQL 18 **isolado**, com as variáveis de testes próprias:

~~~bash
./mvnw -B -Dtest=BrevoApiEmailSenderTest,EmailServiceApiTest,CodigoRecuperacaoTest,RecuperacaoSenhaServiceTest test
./mvnw -B clean verify
~~~

Para a Brevo, usar uma conta de teste com remetente verificado; HTTP **201** significa apenas que a API aceitou o pedido de envio, **não** que o e-mail foi entregue. Conferir eventos de entrega no painel da Brevo.

Validar: resposta genérica para conta inexistente, código expirado/inválido/reutilizado, limites de tentativas, nova senha, login após a redefinição e rejeição de JWT anterior.

## Checklist para publicação controlada no Render

1. **Antes do backend:** confirmar CI do commit candidato e fazer backup novo do PostgreSQL de produção V7. Verificar que o arquivo é legível por `pg_restore --list`, guardar checksum e manter cópia em local protegido. Preferir testar restauração em banco isolado. Não colocar backup no Git.
2. Confirmar plano de recuperação. **Reverter a versão do backend não desfaz as migrações SQL aplicadas por Flyway**; não realizar downgrade manual da base sem procedimento ensaiado.
3. Conferir `MAIL_PROVIDER`, `BREVO_API_KEY`, `MAIL_FROM`, `FRONTEND_URL` e `APP_TRUST_CLOUDFLARE_IP` no Render, sem divulgar os valores secretos. Manter `DB_USER`/`DB_PASSWORD` atuais se já estiverem funcionais. Não aprovar sincronização do Blueprint que proponha alterá-los sem revisão separada.
4. Com aprovação específica, integrar o backend na branch de deploy e executar **deploy manual** com Auto-Deploy/Auto-Sync desativados. A inicialização Spring Boot no plano Free pode ser lenta; examinar logs até `Started GeniusQuestApplication`.
5. Validar `/api/health`, Flyway V11, conectividade do banco, login, categorias e endpoints de administração; interromper a publicação do frontend se algum item falhar.
6. Com backend estável e autorização específica, integrar/publicar o frontend compatível. Testar no Render a recuperação completa com usuário de teste, inclusive entrega do e-mail pela Brevo, expiração e uso único do código.
7. Validar que o link enviado termina em `/redefinir-senha` sem token, que a URL não acumula segredos e que logs não contêm código/chave; confirmar mensagens acessíveis e comportamento de rate limiting.

## Diagnóstico de erros de envio

- Verificar configuração da variável `MAIL_PROVIDER` e presença de `BREVO_API_KEY` / `MAIL_FROM` **sem revelar seus conteúdos**.
- Inspecionar logs sanitizados do backend e o histórico de entregas da Brevo, sem imprimir requisições ou corpos que contenham o código.
- A implementação registra aviso genérico quando o transporte falha. A resposta genérica de solicitação ao usuário não comprova envio ou entrega.
- Para falha em produção, preservar o login já existente, suspender a ativação das novas telas conforme o plano de release e investigar antes de repetir o deploy.
