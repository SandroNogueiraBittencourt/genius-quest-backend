package br.com.geniusquest.controller;

import br.com.geniusquest.dto.pergunta.*;
import br.com.geniusquest.entity.StatusPergunta;
import br.com.geniusquest.service.CuradoriaPerguntaService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Endpoints exclusivos para curadoria ADMIN: incluem gabarito e snapshots de revisão. */
@RestController
@RequestMapping("/api/admin/perguntas")
@PreAuthorize("hasRole('ADMIN')")
public class PerguntaModeracaoController {
    private final CuradoriaPerguntaService service;

    public PerguntaModeracaoController(CuradoriaPerguntaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaPerguntasModeracaoDTO listar(
            @RequestParam(required = false) StatusPergunta status,
            @RequestParam(required = false) UUID temaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listar(status, temaId, page, size);
    }

    @GetMapping("/{id}")
    public PerguntaModeracaoDTO buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoCuradoriaDTO> historico(@PathVariable UUID id) {
        return service.historico(id);
    }

    @PatchMapping("/{id}/revisar")
    public PerguntaModeracaoDTO revisar(@PathVariable UUID id,
            @Valid @RequestBody RevisaoPerguntaDTO pedido, Principal principal) {
        return service.revisar(id, pedido, principal.getName());
    }

    @PatchMapping("/{id}/decisao")
    public PerguntaModeracaoDTO decidir(@PathVariable UUID id,
            @Valid @RequestBody DecisaoPerguntaDTO pedido, Principal principal) {
        return service.decidir(id, pedido, principal.getName());
    }
}
