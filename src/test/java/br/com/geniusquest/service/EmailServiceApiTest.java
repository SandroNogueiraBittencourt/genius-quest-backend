package br.com.geniusquest.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceApiTest {
    @Test
    void apiHttpsNaoFazChamadaSmtpEConservaTemplateHtml() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        BrevoApiEmailSender brevo = mock(BrevoApiEmailSender.class);
        EmailService service = new EmailService(smtp, brevo);
        ReflectionTestUtils.setField(service, "provedor", "brevo-api");
        ReflectionTestUtils.setField(service, "remetente", "remetente@example.com");

        service.enviarLinkRecuperacao("alguem@example.com", "Teste",
                "https://example.com/redefinir-senha?token=teste");

        verify(brevo).enviar(eq("alguem@example.com"), eq("Teste"),
                eq("remetente@example.com"), contains("token=teste"));
        verifyNoInteractions(smtp);
    }

    @Test
    void provedorDesconhecidoNuncaFazEnvio() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        BrevoApiEmailSender brevo = mock(BrevoApiEmailSender.class);
        EmailService service = new EmailService(smtp, brevo);
        ReflectionTestUtils.setField(service, "provedor", "desconhecido");
        ReflectionTestUtils.setField(service, "remetente", "remetente@example.com");

        service.enviarLinkRecuperacao("alguem@example.com", "Teste",
                "https://example.com/redefinir-senha?token=teste");

        verifyNoInteractions(brevo, smtp);
    }
}
