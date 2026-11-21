package br.com.geniusquest.controller;

import br.com.geniusquest.dto.pergunta.*;
import br.com.geniusquest.service.GeracaoIAGatewayService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Somente ADMIN. GET nao gera custo; POST exige UUID idempotente por tentativa. */
@RestController
@RequestMapping("/api/admin/perguntas/geracao")
@PreAuthorize("hasRole('ADMIN')")
public class GeracaoPerguntasIAController {
    private final GeracaoIAGatewayService service;

    public GeracaoPerguntasIAController(GeracaoIAGatewayService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public GeracaoIAStatusDTO status() {
        return service.estado();
    }

    @PostMapping
    public ResponseEntity<GeracaoIAResultadoDTO> gerar(
            @Valid @RequestBody PedidoGeracaoIADTO pedido,
            @RequestHeader("Idempotency-Key") UUID chave,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.gerar(pedido, chave, principal.getName()));
    }
}
