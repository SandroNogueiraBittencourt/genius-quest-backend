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

            String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:32px 16px;background:#f5f3ff;font-family:Arial,Helvetica,sans-serif;color:#1a1530">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0"><tr><td align="center">
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0"
                      style="max-width:600px;background:#fff;border-radius:20px;overflow:hidden">
                      <tr><td style="background:#1a1530;padding:28px 32px;color:#fff">
                        <strong style="font-size:24px">Genius <span style="color:#ffb347">Quest</span></strong>
                        <p style="font-size:14px;color:#d9d2eb">Curiosidade que aproxima.</p>
                      </td></tr>
                      <tr><td style="padding:32px">
                        <h1 style="font-size:26px">Recuperar acesso</h1>
                        <p>Olá, <strong>{{NOME}}</strong>!</p>
                        <p>Recebemos um pedido de redefinição da senha da sua conta.</p>
                        <p>Digite este código no aplicativo:</p>
                        <p style="font-family:monospace;font-size:25px;font-weight:bold;letter-spacing:2px;
                           padding:18px 12px;background:#eee9ff;border-radius:12px;text-align:center">{{CODIGO}}</p>
                        <p>O código expira em <strong>30 minutos</strong>, só pode ser usado uma vez e não deve ser compartilhado.</p>
                        <p><a href="{{URL}}" style="display:inline-block;padding:14px 18px;
                           border-radius:12px;background:#ffb347;color:#1a1530;text-decoration:none;font-weight:bold">
                           Abrir página de redefinição</a></p>
                        <p style="color:#605672;font-size:14px">Se você não solicitou a recuperação, ignore este e-mail.</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """
                .replace("{{NOME}}", HtmlUtils.htmlEscape(nome))
                .replace("{{CODIGO}}", HtmlUtils.htmlEscape(CodigoRecuperacao.paraExibicao(codigo)))
                .replace("{{URL}}", HtmlUtils.htmlEscape(urlPagina));

            if ("brevo-api".equalsIgnoreCase(provedor)) {
                brevoApi.enviar(destinatario, nome, remetente, html);
            } else if ("smtp".equalsIgnoreCase(provedor)) {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
                helper.setFrom(remetente, "Genius Quest");
                helper.setTo(destinatario);
                helper.setSubject("Genius Quest: código de recuperação");
                helper.setText(html, true);
                mailSender.send(msg);
            } else {
                throw new IllegalStateException("MAIL_PROVIDER invalido");
            }
        } catch (Exception e) {
            // Nunca registrar link, codigo, email, chave API ou body da mensagem.
            log.warn("Falha no transporte de recuperacao (classe={})", e.getClass().getSimpleName());
        }
    }
}
