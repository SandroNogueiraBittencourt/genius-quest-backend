package br.com.geniusquest.controller;

import br.com.geniusquest.dto.partida.SalaResponseDTO;
import br.com.geniusquest.service.SalaService;
import br.com.geniusquest.service.SalaService.Votacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
public class SalaController {
    private final SalaService salas;

    public SalaController(SalaService salas) {
        this.salas = salas;
    }

    @PostMapping("/api/salas")
    public ResponseEntity<SalaResponseDTO> criar(Principal principal) {
        SalaResponseDTO sala = salas.criar(principal.getName());
        return ResponseEntity.created(URI.create("/api/salas/" + sala.codigo())).body(sala);
    }

    @PutMapping("/api/salas/{codigo}/tema")
    public SalaResponseDTO escolherTema(@PathVariable UUID codigo,
            @Valid @RequestBody EscolhaTema pedido, Principal principal) {
        return salas.escolherTema(codigo, pedido.temaId(), principal.getName());
    }

    public record EscolhaTema(@NotNull UUID temaId) {}

    @GetMapping("/api/salas/{codigo}/votacao")
    public Votacao consultarVotacao(@PathVariable UUID codigo, Principal principal) {
        return salas.consultarVotacao(codigo, principal.getName());
    }

    @PostMapping("/api/salas/{codigo}/votacao")
    public Votacao abrirVotacao(@PathVariable UUID codigo, Principal principal) {
        return salas.abrirVotacao(codigo, principal.getName());
    }

    @PutMapping("/api/salas/{codigo}/votacao/voto")
    public Votacao votar(@PathVariable UUID codigo, @Valid @RequestBody EscolhaTema pedido, Principal principal) {
        return salas.registrarVoto(codigo, pedido.temaId(), principal.getName());
    }

    @DeleteMapping("/api/salas/{codigo}/votacao/voto")
    public Votacao retirarVoto(@PathVariable UUID codigo, Principal principal) {
        return salas.registrarVoto(codigo, null, principal.getName());
    }

    @PostMapping("/api/salas/{codigo}/votacao/encerrar")
    public SalaResponseDTO encerrarVotacao(@PathVariable UUID codigo,
            @RequestParam(required = false) UUID desempate, Principal principal) {
        return salas.encerrarVotacao(codigo, desempate, principal.getName());
    }

    @PostMapping("/api/partidas/{partidaId}/sala")
    public SalaResponseDTO criar(@PathVariable UUID partidaId, Principal principal) {
        return salas.criar(partidaId, principal.getName());
    }

    @PostMapping("/api/salas/{codigo}/entrada")
    public SalaResponseDTO entrar(@PathVariable UUID codigo, Principal principal) {
        return salas.entrar(codigo, principal.getName());
    }

    @GetMapping("/api/salas/{codigo}")
    public SalaResponseDTO consultar(@PathVariable UUID codigo, Principal principal) {
        return salas.consultar(codigo, principal.getName());
    }
}
