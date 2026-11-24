package br.com.geniusquest.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceApiTest {
    @Test
    void codigoSoVaiNoCorpoDeTextoEButtonHrefNaoLevaSegredo() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        BrevoApiEmailSender brevo = mock(BrevoApiEmailSender.class);
        EmailService service = new EmailService(smtp, brevo);
        ReflectionTestUtils.setField(service, "provedor", "brevo-api");
        ReflectionTestUtils.setField(service, "remetente", "remetente@example.com");
        String codigo = "ABCD2345EFGH6789";
        service.enviarCodigoRecuperacao("alguem@example.com", "Teste", codigo,
                "https://genius-quest-web.onrender.com/redefinir-senha");

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(brevo).enviar(eq("alguem@example.com"), eq("Teste"),
                eq("remetente@example.com"), corpo.capture());
        assertTrue(corpo.getValue().contains("ABCD-2345-EFGH-6789"));
        assertTrue(corpo.getValue().contains("30 minutos"));
        assertTrue(corpo.getValue().contains("href=\"https://genius-quest-web.onrender.com/redefinir-senha\""));
        assertFalse(corpo.getValue().contains("?token="));
        assertFalse(corpo.getValue().contains("?codigo="));
        verifyNoInteractions(smtp);
    }

    @Test
    void verificacaoUsaModeloComCodigoEValidade() {
        BrevoApiEmailSender brevo = mock(BrevoApiEmailSender.class);
        EmailService service = new EmailService(mock(JavaMailSender.class), brevo);
        ReflectionTestUtils.setField(service, "provedor", "brevo-api");
        ReflectionTestUtils.setField(service, "remetente", "remetente@example.com");

        service.enviarCodigoVerificacao("alguem@example.com", "Teste", "0123456789");

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(brevo).enviar(eq("alguem@example.com"), eq("Teste"),
                eq("remetente@example.com"), eq("Genius Quest: confirme seu e-mail"), corpo.capture());
        assertTrue(corpo.getValue().contains("0123456789"));
        assertTrue(corpo.getValue().contains("15 minutos"));
        assertTrue(corpo.getValue().contains("Curiosidade que aproxima"));
        assertFalse(corpo.getValue().contains("href="));
    }

    @Test
    void provedorDesconhecidoNaoEnviaEmail() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        BrevoApiEmailSender brevo = mock(BrevoApiEmailSender.class);
        EmailService service = new EmailService(smtp, brevo);
        ReflectionTestUtils.setField(service, "provedor", "desconhecido");
        ReflectionTestUtils.setField(service, "remetente", "remetente@example.com");
        service.enviarCodigoRecuperacao("alguem@example.com", "Teste",
                "ABCD2345EFGH6789", "http://localhost:5173/redefinir-senha");
        verifyNoInteractions(brevo, smtp);
    }
}
