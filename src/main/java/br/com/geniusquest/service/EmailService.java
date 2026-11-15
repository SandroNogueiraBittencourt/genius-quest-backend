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
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                        <h2>Recuperação de senha</h2>
                        <p>Olá, %s! Recebemos um pedido para redefinir sua senha.</p>
                        <p><a href="%s" style="background:#0d6efd;color:#fff;padding:12px 20px;
                           text-decoration:none;border-radius:6px;">Redefinir senha</a></p>
                        <p style="color:#666;font-size:13px;">O link expira em 30 minutos e só pode ser usado uma vez.
                           Se você não pediu isso, ignore este e-mail.</p>
                    </div>
                    """.formatted(HtmlUtils.htmlEscape(nome), link);

            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail de recuperação: {}", e.getMessage());
        }
    }
}
