package br.com.geniusquest.service;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final BrevoApiEmailSender brevoApi;

    @Value("${app.mail.provider:smtp}")
    private String provedor;

    @Value("${app.mail.from:}")
    private String remetente;

    public EmailService(JavaMailSender mailSender, BrevoApiEmailSender brevoApi) {
        this.mailSender = mailSender;
        this.brevoApi = brevoApi;
    }

    @Async
    public void enviarCodigoVerificacao(String destinatario, String nome, String codigo) {
        try {
            String assunto = "Genius Quest: confirme seu e-mail";
            String digitos = CodigoVerificacaoEmail.normalizar(codigo);
            if (!CodigoVerificacaoEmail.formatoValido(digitos)) {
                throw new IllegalArgumentException("Formato de código inválido");
            }
            String codigoExibido = digitos.replaceAll("([0-9]{2})(?=[0-9])", "$1 ");
            String html = emailComCodigo(nome, codigoExibido, "Confirme seu e-mail",
                    "Use o código abaixo para confirmar seu endereço de e-mail.",
                    "", "Se você não solicitou esta confirmação, ignore este e-mail.", CodigoVerificacaoEmail.VALIDADE_MINUTOS);
            if ("brevo-api".equalsIgnoreCase(provedor)) {
                brevoApi.enviar(destinatario, nome, remetente, assunto, html);
            } else if ("smtp".equalsIgnoreCase(provedor)) {
                enviarSmtp(destinatario, assunto, html);
            } else {
                throw new IllegalStateException("MAIL_PROVIDER invalido");
            }
        } catch (Exception e) {
            log.warn("Falha no transporte de verificação de e-mail (classe={})", e.getClass().getSimpleName());
        }
    }

    private void enviarSmtp(String destinatario, String assunto, String html) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setFrom(remetente, "Genius Quest");
        helper.setTo(destinatario);
        helper.setSubject(assunto);
        helper.setText(html, true);
        mailSender.send(msg);
    }

    /**
     * O codigo deve existir apenas no texto do e-mail, nunca em URLs.
     * A pagina de redefinicao nao recebe segredo em query, fragmento ou path.
     */
    @Async
    public void enviarCodigoRecuperacao(String destinatario, String nome,
                                        String codigo, String urlPagina) {
        try {
            if (!CodigoRecuperacao.formatoValido(codigo)) {
                throw new IllegalArgumentException("Formato de codigo invalido");
            }
            // Configuracao e controlada pelo servidor; URL nunca pode conter query/fragmento.
            java.net.URI uri = java.net.URI.create(urlPagina);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Protocolo de recuperacao invalido");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Pagina com parametros nao permitidos");
            }

            String acao = """
                    <p><a href="%s" style="display:inline-block;padding:14px 18px;
                       border-radius:12px;background:#ffb347;color:#1a1530;font-weight:bold">
                       Abrir página de redefinição</a></p>
                    """.formatted(HtmlUtils.htmlEscape(urlPagina));
            String html = emailComCodigo(nome, CodigoRecuperacao.paraExibicao(CodigoRecuperacao.normalizar(codigo)), "Recuperar acesso",
                    "Recebemos um pedido de redefinição da sua senha. Digite o código no aplicativo.",
                    acao, "Se você não solicitou a recuperação, ignore este e-mail.", 30);
            if ("brevo-api".equalsIgnoreCase(provedor)) {
                brevoApi.enviar(destinatario, nome, remetente, html);
            } else if ("smtp".equalsIgnoreCase(provedor)) {
                enviarSmtp(destinatario, "Genius Quest: código de recuperação", html);
            } else {
                throw new IllegalStateException("MAIL_PROVIDER invalido");
            }
        } catch (Exception e) {
            // Nunca registrar link, codigo, email, chave API ou body da mensagem.
            log.warn("Falha no transporte de recuperacao (classe={})", e.getClass().getSimpleName());
        }
    }

    private String emailComCodigo(String nome, String codigo, String titulo, String intro,
            String acao, String rodape, long validade) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="format-detection" content="telephone=no"></head>
                <body style="margin:0;padding:32px 16px;background:#f5f3ff;font-family:Arial,Helvetica,sans-serif;color:#1a1530">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0"><tr><td align="center">
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0"
                      style="max-width:600px;background:#fff;border-radius:20px;overflow:hidden">
                      <tr><td style="background:#1a1530;padding:28px 32px;color:#fff">
                        <strong style="font-size:24px">Genius <span style="color:#ffb347">Quest</span></strong>
                        <p style="font-size:14px;color:#d9d2eb">Curiosidade que aproxima.</p>
                      </td></tr>
                      <tr><td style="padding:32px">
                        <h1 style="font-size:26px">{{TITULO}}</h1>
                        <p>Olá, <strong>{{NOME}}</strong>!</p>
                        <p>{{INTRO}}</p>
                        <p style="font-family:monospace;font-size:25px;font-weight:bold;letter-spacing:2px;
                           padding:18px 12px;background:#eee9ff;border-radius:12px;text-align:center">{{CODIGO}}</p>
                        <p>O código expira em <strong>{{VALIDADE}} minutos</strong>, só pode ser usado uma vez e não deve ser compartilhado.</p>
                        {{ACAO}}
                        <p style="color:#605672;font-size:14px">{{RODAPE}}</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """
                .replace("{{TITULO}}", titulo)
                .replace("{{INTRO}}", intro)
                .replace("{{CODIGO}}", codigo)
                .replace("{{VALIDADE}}", Long.toString(validade))
                .replace("{{ACAO}}", acao)
                .replace("{{RODAPE}}", rodape)
                .replace("{{NOME}}", HtmlUtils.htmlEscape(nome));
    }
}
