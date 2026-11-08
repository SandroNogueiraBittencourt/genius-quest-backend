package br.com.geniusquest.controller;

import br.com.geniusquest.dto.tema.*;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.service.TemaService;
import br.com.geniusquest.service.VotacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/temas")
public class TemaController {
    private final TemaService temas;
    private final VotacaoService votacao;

    public TemaController(TemaService temas, VotacaoService votacao) {
        this.temas = temas;
        this.votacao = votacao;
    }

    @PostMapping
    public ResponseEntity<TemaResponseDTO> sugerir(@Valid @RequestBody TemaCadastroDTO dto,
            Authentication auth) {
        TemaResponseDTO tema = temas.sugerir(dto, auth.getName());
        return ResponseEntity.created(URI.create("/api/temas/" + tema.id())).body(tema);
    }

    @GetMapping
    public List<TemaResponseDTO> listarAprovados() {
        return temas.listarAprovados();
    }

    @GetMapping("/moderacao")
    public List<TemaResponseDTO> listarModeracao() {
        return temas.listarModeracao();
    }

    @GetMapping("/{id}")
    public TemaResponseDTO buscar(@PathVariable UUID id, Authentication auth) {
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return temas.buscarVisivel(id, auth.getName(), admin);
    }

    @PatchMapping("/{id}")
    public TemaResponseDTO atualizar(@PathVariable UUID id, @Valid @RequestBody TemaAtualizacaoDTO dto) {
        return temas.atualizar(id, dto);
    }

    @PatchMapping("/{id}/aprovar")
    public TemaResponseDTO aprovar(@PathVariable UUID id) {
        return temas.moderar(id, StatusTema.APROVADO);
    }

    @PatchMapping("/{id}/suspender")
    public TemaResponseDTO suspender(@PathVariable UUID id) {
        return temas.moderar(id, StatusTema.SUSPENSO);
    }

    @PatchMapping("/{id}/remover")
    public TemaResponseDTO remover(@PathVariable UUID id) {
        return temas.moderar(id, StatusTema.REMOVIDO);
    }

    @PostMapping("/{id}/votos")
    public ResponseEntity<VotoResponseDTO> votar(@PathVariable UUID id, Authentication auth) {
        VotoResponseDTO voto = votacao.votar(id, auth.getName());
        return ResponseEntity.created(URI.create("/api/temas/" + id + "/votos/" + voto.id())).body(voto);
    }
}
