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

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${app.mail.from:}")
    private String remetente;

    @Async
    public void enviarLinkRecuperacao(String destinatario, String nome, String link) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(remetente, "Genius Quest");
            helper.setTo(destinatario);
            helper.setSubject("Genius Quest: recuperação de senha");

            String html = """
                    <!DOCTYPE html>
                    <html lang="pt-BR">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Recuperação de senha — Genius Quest</title>
                    </head>
                    <body style="margin:0;padding:0;background-color:#f5f3ff;font-family:Arial,Helvetica,sans-serif;color:#1a1530;">
                        <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
                            Redefina sua senha do Genius Quest. Seu link é válido por 30 minutos.
                        </div>

                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                               style="background-color:#f5f3ff;">
                            <tr>
                                <td align="center" style="padding:40px 16px;">
                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                                           style="max-width:600px;background-color:#ffffff;border:1px solid #c8bedd;border-radius:24px;overflow:hidden;">
                                        <tr>
                                            <td style="padding:32px;background-color:#1a1530;border-radius:24px 24px 0 0;">
                                                <p style="margin:0;font-size:24px;font-weight:800;letter-spacing:-0.5px;color:#ffffff;">
                                                    Genius <span style="color:#ffb347;">Quest</span>
                                                </p>
                                                <p style="margin:12px 0 0;font-size:14px;line-height:1.6;color:#c9c2da;">
                                                    Curiosidade que aproxima.
                                                </p>
                                            </td>
                                        </tr>

                                        <tr>
                                            <td style="padding:32px;">
                                                <p style="margin:0 0 12px;font-size:12px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#6c3bff;">
                                                    Sua conta Genius Quest
                                                </p>

                                                <h1 style="margin:0 0 24px;font-size:28px;line-height:1.3;letter-spacing:-0.8px;color:#1a1530;">
                                                    Vamos recuperar seu acesso?
                                                </h1>

                                                <p style="margin:0 0 16px;font-size:16px;line-height:1.7;color:#1a1530;">
                                                    Olá, <strong>%s!</strong>
                                                </p>

                                                <p style="margin:0 0 28px;font-size:16px;line-height:1.7;color:#605672;">
                                                    Recebemos um pedido para redefinir a senha da sua conta.
                                                    Clique no botão abaixo para escolher uma nova senha e
                                                    continuar sua jornada.
                                                </p>

                                                <table role="presentation" cellspacing="0" cellpadding="0">
                                                    <tr>
                                                        <td align="center" bgcolor="#ffb347" style="border-radius:16px;">
                                                            <a href="%s"
                                                               style="display:inline-block;padding:17px 28px;border:1px solid #ffb347;border-radius:16px;font-size:16px;font-weight:700;line-height:1.4;color:#1a1530;text-decoration:none;">
                                                                Redefinir minha senha
                                                            </a>
                                                        </td>
                                                    </tr>
                                                </table>

                                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                                                       style="margin-top:28px;">
                                                    <tr>
                                                        <td style="padding:18px 20px;background-color:#eee9ff;border-radius:16px;">
                                                            <p style="margin:0;font-size:14px;line-height:1.7;color:#1a1530;">
                                                                <strong>O link expira em 30 minutos</strong>
                                                                e só pode ser usado uma vez.
                                                                Não compartilhe este e-mail ou o link de recuperação.
                                                            </p>
                                                        </td>
                                                    </tr>
                                                </table>

                                                <p style="margin:24px 0 0;font-size:14px;line-height:1.7;color:#605672;">
                                                    Se você não solicitou essa alteração, ignore este e-mail.
                                                    Sua senha continuará a mesma.
                                                </p>
                                            </td>
                                        </tr>

                                        <tr>
                                            <td align="center" style="padding:24px 32px;border-top:1px solid #eee9ff;">
                                                <p style="margin:0;font-size:12px;line-height:1.7;color:#605672;">
                                                    Genius Quest · Aprenda. Jogue. Conquiste.
                                                </p>
                                                <p style="margin:6px 0 0;font-size:12px;line-height:1.7;color:#605672;">
                                                    Este é um e-mail automático. Não é necessário responder.
                                                </p>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
                    </body>
                    </html>
                    """
                    .formatted(
                            HtmlUtils.htmlEscape(nome),
                            HtmlUtils.htmlEscape(link),
                            HtmlUtils.htmlEscape(link));

            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de recuperação: {}", e.getMessage());
        }
    }
}
