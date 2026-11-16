package br.com.geniusquest.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geniusquest.dto.recuperacao_senha.EsqueciSeanhaRequest;
import br.com.geniusquest.dto.recuperacao_senha.RedefinirSenhaRequest;
import br.com.geniusquest.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class RecuperarSenhaController {
    private final RecuperacaoSenhaService service;

    public RecuperarSenhaController(RecuperacaoSenhaService service) {
        this.service = service;
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Map<String, String>> esqueciSenha(@Valid @RequestBody EsqueciSeanhaRequest req) {
        service.solicitar(req.email());
        return ResponseEntity.ok(Map.of(
                "mensagem", "Se o e-mail estiver cadastrado, enviaremos um link de recuperação."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        service.redefinir(req.token(), req.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
