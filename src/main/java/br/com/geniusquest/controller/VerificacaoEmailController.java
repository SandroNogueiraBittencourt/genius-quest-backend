package br.com.geniusquest.controller;

import br.com.geniusquest.service.VerificacaoEmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class VerificacaoEmailController {
    private final VerificacaoEmailService verificacao;

    @Value("${app.security.trust-cloudflare-client-ip:false}")
    private boolean confiarIpCloudflare;

    public VerificacaoEmailController(VerificacaoEmailService verificacao) {
        this.verificacao = verificacao;
    }

    @PostMapping("/verificar-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verificar(@Valid @RequestBody Confirmacao pedido, HttpServletRequest request) {
        verificacao.verificar(pedido.email(), pedido.codigo(),
                RecuperarSenhaController.origem(request, confiarIpCloudflare));
    }

    @PostMapping("/reenviar-verificacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviar(@Valid @RequestBody Reenvio pedido, HttpServletRequest request) {
        verificacao.reenviar(pedido.email(),
                RecuperarSenhaController.origem(request, confiarIpCloudflare));
    }

    public record Confirmacao(@NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 64) String codigo) {}

    public record Reenvio(@NotBlank @Email @Size(max = 150) String email) {}
}
