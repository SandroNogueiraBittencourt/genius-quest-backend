package br.com.geniusquest.controller;

import br.com.geniusquest.dto.partida.ContinuacaoSalaDTO;
import br.com.geniusquest.dto.partida.PerguntaPartidaDTO;
import br.com.geniusquest.dto.partida.RespostaSalaDTO;
import br.com.geniusquest.dto.partida.ResultadoPartidaDTO;
import br.com.geniusquest.dto.partida.SalaResponseDTO;
import br.com.geniusquest.service.SalaService;
import br.com.geniusquest.service.SalaService.Votacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

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

    @DeleteMapping("/api/salas/{codigo}/entrada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@PathVariable UUID codigo, Principal principal) {
        salas.sair(codigo, principal.getName());
    }

    @DeleteMapping("/api/salas/{codigo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fecharSala(@PathVariable UUID codigo, Principal principal) {
        salas.fecharSala(codigo, principal.getName());
    }

    @GetMapping("/api/salas/{codigo}")
    public SalaResponseDTO consultar(@PathVariable UUID codigo, Principal principal) {
        return salas.consultar(codigo, principal.getName());
    }

    @PostMapping("/api/salas/{codigo}/partida/iniciar")
    public PerguntaPartidaDTO iniciarPartida(@PathVariable UUID codigo, Principal principal) {
        return salas.iniciarPartida(codigo, principal.getName());
    }

    @GetMapping("/api/salas/{codigo}/partida/pergunta-atual")
    public PerguntaPartidaDTO consultarPerguntaAtual(@PathVariable UUID codigo, Principal principal) {
        return salas.consultarPerguntaAtual(codigo, principal.getName());
    }

    @PostMapping("/api/salas/{codigo}/partida/respostas")
    public RespostaSalaDTO responder(@PathVariable UUID codigo, @Valid @RequestBody Resposta pedido,
            Principal principal) {
        return salas.responder(codigo, pedido.numero(), pedido.alternativaIndice(), principal.getName());
    }

    public record Resposta(@NotNull @Min(1) @Max(10) Integer numero,
            @NotNull @Min(0) @Max(3) Integer alternativaIndice) {}

    @GetMapping("/api/salas/{codigo}/partidas/{partidaId}/continuacao")
    public ContinuacaoSalaDTO consultarContinuacao(@PathVariable UUID codigo, @PathVariable UUID partidaId,
            Principal principal) {
        return salas.consultarContinuacao(codigo, partidaId, principal.getName());
    }

    @PutMapping("/api/salas/{codigo}/partidas/{partidaId}/continuacao")
    public ContinuacaoSalaDTO votarContinuacao(@PathVariable UUID codigo, @PathVariable UUID partidaId,
            @Valid @RequestBody VotoContinuacao pedido, Principal principal) {
        return salas.votarContinuacao(codigo, partidaId, pedido.continuar(), principal.getName());
    }

    public record VotoContinuacao(@NotNull Boolean continuar) {}

    @GetMapping("/api/salas/{codigo}/partidas/{partidaId}/resultado")
    public ResultadoPartidaDTO consultarResultado(@PathVariable UUID codigo, @PathVariable UUID partidaId,
            Principal principal) {
        return salas.consultarResultado(codigo, partidaId, principal.getName());
    }
}
