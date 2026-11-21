package br.com.geniusquest.controller;

import br.com.geniusquest.dto.pergunta.*;
import br.com.geniusquest.entity.StatusPergunta;
import br.com.geniusquest.service.CuradoriaPerguntaService;
import br.com.geniusquest.service.CriacaoManualPerguntaService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Endpoints exclusivos para curadoria ADMIN: incluem gabarito e snapshots de revisão. */
@RestController
@RequestMapping("/api/admin/perguntas")
@PreAuthorize("hasRole('ADMIN')")
public class PerguntaModeracaoController {
    private final CuradoriaPerguntaService service;
    private final CriacaoManualPerguntaService criacaoManual;

    public PerguntaModeracaoController(CuradoriaPerguntaService service,
            CriacaoManualPerguntaService criacaoManual) {
        this.service = service;
        this.criacaoManual = criacaoManual;
    }

    /** Cria candidata manual PENDENTE; gabarito fica restrito à consulta ADMIN. */
    @PostMapping
    public ResponseEntity<Map<String, UUID>> criarManual(
            @Valid @RequestBody CriacaoPerguntaManualDTO pedido) {
        UUID id = criacaoManual.criar(pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
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
