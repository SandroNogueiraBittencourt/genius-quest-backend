# Envio de e-mails de recuperação pela API HTTPS da Brevo

## Objetivo

Usar HTTPS/443 em vez de SMTP no backend hospedado no Render Free.
A integração usa o endpoint fixo POST https://api.brevo.com/v3/smtp/email
e mantém SMTP para desenvolvimento local e Mailpit.

## Variáveis

- MAIL_PROVIDER=brevo-api
- BREVO_API_KEY=<chave de API da Brevo, NAO a chave SMTP>
- MAIL_FROM=<endereco remetente registrado e verificado na Brevo>
- FRONTEND_URL=https://genius-quest-web.onrender.com

No ambiente local, MAIL_PROVIDER=smtp continua sendo o padrao.
Nao comite segredos, imprima tokens em logs ou copie credenciais para issues/PRs.
Nao defina BREVO_API_KEY no Render antes do deploy controlado, se alterar env acionar deploy.

## Protecao do link de recuperacao

O HTML contem um token de uso unico na URL. A Brevo pode reescrever links
para rastrear cliques. O campo contactPixelTrackingConsent=false foi incluido
como uma preferencia de privacidade, MAS seu efeito depende da funcionalidade de
consentimento estar ativada na conta, e nao comprova que a URL nao foi reescrita.

ANTES de ativar em producao:
1. Habilite/valide configuracao de consentimento para rastreamento no painel Brevo.
2. Gere um email de recuperacao numa conta de teste.
3. Inspecione o destino real do botao SEM compartilhar o token: a URL nao deve
   passar por dominio de rastreamento/redirect da Brevo.
4. Se houver redirecionamento, nao habilite recuperacao em producao ate
   encontrar uma configuracao segura ou alterar o fluxo para codigo manual.
5. Verifique que o token continua valido por 30 minutos e e de uso unico.

## Testes

- ./mvnw -B -Dtest=BrevoApiEmailSenderTest,EmailServiceApiTest test
- ./mvnw -B clean verify (com PostgreSQL 18 de testes, nao producao)
- Confirmar autenticação da API com chave real em execucao local isolada,
  entrega do email, URL original nao rastreada e redefinicao concluida.
- Render: revisar o endpoint HTTPS e status dos envios SEM registrar corpo da requisicao.

## Resposta a erros

A API retorna HTTP 201 quando aceita o pedido de envio.
Isso NAO garante entrega final: verificar Logs/Delivered na Brevo.
O codigo so registra o status HTTP e nunca a resposta completa ou o token.
