package br.com.geniusquest.controller;

import br.com.geniusquest.dto.partida.*;
import br.com.geniusquest.service.PartidaService;
import br.com.geniusquest.service.JogoPartidaService;
import br.com.geniusquest.web.Paginacao;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
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
    private final JogoPartidaService jogo;

    public PartidaController(PartidaService service, JogoPartidaService jogo) {
        this.service = service;
        this.jogo = jogo;
    }

    @PostMapping
    public ResponseEntity<PartidaResponseDTO> preparar(
            @Valid @RequestBody PartidaCadastroDTO pedido, Principal principal) {
        PartidaResponseDTO criada = service.preparar(pedido, principal.getName());
        return ResponseEntity.created(URI.create("/api/partidas/" + criada.id()))
                .body(criada);
    }

    @GetMapping(value = "/minhas", params = "!page")
    public List<PartidaResponseDTO> minhas(Principal principal) {
        return service.minhas(principal.getName());
    }

    @GetMapping(value = "/minhas", params = "page")
    public PagedModel<PartidaResponseDTO> minhasPaginado(
            Principal principal, @RequestParam int page, @RequestParam(defaultValue = "10") int size) {
        return new PagedModel<>(service.minhas(principal.getName(),
                Paginacao.criar(page, size, Sort.by(Sort.Direction.DESC, "criadoEm", "id"))));
    }

    @GetMapping("/{id}")
    public PartidaResponseDTO buscar(@PathVariable UUID id, Principal principal) {
        return service.buscar(id, principal.getName());
    }

    /**
     * Prévia de até dez perguntas aprovadas do tema vencedor.
     * Não inicia nem pontua o quiz e nunca devolve o gabarito.
     */
    @PostMapping("/{id}/iniciar")
    public PartidaResponseDTO iniciar(@PathVariable UUID id, Principal principal) {
        return jogo.iniciar(id, principal.getName());
    }

    @PostMapping("/{id}/respostas")
    public ResponseEntity<RespostaPartidaDTO> responder(
            @PathVariable UUID id, @Valid @RequestBody RespostaPartidaCadastroDTO pedido,
            Principal principal) {
        RespostaPartidaDTO criada = jogo.responder(id, pedido, principal.getName());
        return ResponseEntity.status(201).body(criada);
    }

    @GetMapping("/{id}/respostas")
    public List<RespostaPartidaDTO> minhasRespostas(@PathVariable UUID id, Principal principal) {
        return jogo.minhasRespostas(id, principal.getName());
    }

    @GetMapping("/{id}/perguntas")
    public List<PerguntaQuizDTO> perguntasElegiveis(@PathVariable UUID id, Principal principal) {
        return service.perguntasElegiveis(id, principal.getName());
    }
}
