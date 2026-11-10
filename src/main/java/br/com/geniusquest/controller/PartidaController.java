package br.com.geniusquest.controller;

import br.com.geniusquest.dto.partida.*;
import br.com.geniusquest.service.PartidaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/partidas")
public class PartidaController {
    private final PartidaService service;

    public PartidaController(PartidaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PartidaResponseDTO> preparar(
            @Valid @RequestBody PartidaCadastroDTO pedido, Principal principal) {
        PartidaResponseDTO criada = service.preparar(pedido, principal.getName());
        return ResponseEntity.created(URI.create("/api/partidas/" + criada.id()))
                .body(criada);
    }

    @GetMapping("/minhas")
    public List<PartidaResponseDTO> minhas(Principal principal) {
        return service.minhas(principal.getName());
    }

    @GetMapping("/{id}")
    public PartidaResponseDTO buscar(@PathVariable UUID id, Principal principal) {
        return service.buscar(id, principal.getName());
    }
}
