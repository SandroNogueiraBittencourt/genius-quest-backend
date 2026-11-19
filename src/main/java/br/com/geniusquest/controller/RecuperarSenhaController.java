package br.com.geniusquest.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geniusquest.dto.recuperacao_senha.EsqueciSeanhaRequest;
import br.com.geniusquest.dto.recuperacao_senha.RedefinirSenhaRequest;
import br.com.geniusquest.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class RecuperarSenhaController {
    private final RecuperacaoSenhaService service;

    // Ative apenas no Render publico (Cloudflare reescreve CF-Connecting-IP).
    @Value("${app.security.trust-cloudflare-client-ip:false}")
    private boolean confiarIpCloudflare;

    public RecuperarSenhaController(RecuperacaoSenhaService service) {
        this.service = service;
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Map<String, String>> esqueciSenha(
            @Valid @RequestBody EsqueciSeanhaRequest req, HttpServletRequest request) {
        String origem = request.getRemoteAddr();
        // Nao usar X-Forwarded-For diretamente: o cliente pode inserir valores falsos.
        // O cabecalho CF-Connecting-IP e confiavel APENAS no ingress publico do Render.
        if (confiarIpCloudflare) {
            String cfIp = request.getHeader("CF-Connecting-IP");
            if (cfIp != null) {
                String candidato = cfIp.trim();
                // Formato de IP literal, sem listas, nomes de host ou caracteres extras.
                if (candidato.length() <= 45
                        && candidato.matches("[0-9a-fA-F:.]+")
                        && (candidato.contains(":") || candidato.contains("."))) {
                    origem = candidato;
                }
            }
        }
        service.solicitar(req.email(), origem);
        return ResponseEntity.ok(Map.of(
                "mensagem", "Se o e-mail estiver cadastrado, enviaremos um link de recuperação."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        service.redefinir(req.token(), req.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
