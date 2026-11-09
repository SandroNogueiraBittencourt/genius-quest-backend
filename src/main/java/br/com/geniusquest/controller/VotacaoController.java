package br.com.geniusquest.controller;

import br.com.geniusquest.dto.tema.*;
import br.com.geniusquest.service.VotacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ciclos-votacao")
public class VotacaoController {
    private final VotacaoService service;

    public VotacaoController(VotacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CicloResponseDTO> criar(@Valid @RequestBody CicloCadastroDTO dto) {
        CicloResponseDTO ciclo = service.criarCiclo(dto);
        return ResponseEntity.created(URI.create("/api/ciclos-votacao/" + ciclo.id())).body(ciclo);
    }

    @GetMapping("/historico")
    public HistoricoVotacoesDTO historico(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.historico(page, size);
    }

    @GetMapping("/atual")
    public CicloResponseDTO atual() {
        return service.cicloAtual();
    }

    @GetMapping("/{id}/ranking")
    public List<TemaRankingDTO> ranking(@PathVariable UUID id) {
        return service.ranking(id);
    }

    @PatchMapping("/{id}/encerrar")
    public CicloResponseDTO encerrar(@PathVariable UUID id) {
        return service.encerrar(id);
    }
}
