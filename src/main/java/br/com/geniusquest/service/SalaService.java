package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.SalaResponseDTO;
import br.com.geniusquest.entity.Partida;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.entity.StatusPartida;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.PartidaRepository;
import br.com.geniusquest.repository.SalaRepository;
import br.com.geniusquest.repository.SalaRepository.Sala;
import br.com.geniusquest.repository.SalaRepository.TotalVotos;
import br.com.geniusquest.repository.TemaRepository;
import br.com.geniusquest.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SalaService {
    public record Votacao(boolean aberta, UUID meuVoto, List<TotalVotos> totais) {}

    private final SalaRepository salas;
    private final PartidaRepository partidas;
    private final UsuarioRepository usuarios;
    private final TemaRepository temas;
    private final PartidaService preparacao;

    public SalaService(SalaRepository salas, PartidaRepository partidas, UsuarioRepository usuarios,
            TemaRepository temas, PartidaService preparacao) {
        this.salas = salas;
        this.partidas = partidas;
        this.usuarios = usuarios;
        this.temas = temas;
        this.preparacao = preparacao;
    }

    @Transactional
    public SalaResponseDTO criar(String email) {
        UUID anfitriaoId = usuarioAtivo(email).getId();
        UUID codigo = abrirSala(null, anfitriaoId);
        return resposta(codigo, anfitriaoId, null);
    }

    @Transactional
    public SalaResponseDTO criar(UUID partidaId, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Partida partida = partidas.buscarParaAtualizacao(partidaId)
                .filter(p -> p.getCriador().getId().equals(usuarioId))
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Partida não encontrada"));

        UUID codigo = salas.buscarCodigo(partidaId).orElseGet(() -> {
            exigirPreparacao(partida);
            return abrirSala(partidaId, usuarioId);
        });
        return resposta(codigo, usuarioId, partida);
    }

    private UUID abrirSala(UUID partidaId, UUID anfitriaoId) {
        UUID codigo = UUID.randomUUID();
        salas.criar(codigo, partidaId, anfitriaoId);
        salas.adicionarParticipante(codigo, anfitriaoId);
        return codigo;
    }

    @Transactional
    public SalaResponseDTO escolherTema(UUID codigo, UUID temaId, String email) {
        Usuario anfitriao = usuarioAtivo(email);
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirAnfitriao(codigo, sala, anfitriao);
        if (sala.votacaoAberta()) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Encerre a votação para definir o tema");
        }

        Partida partida = bloquearPartida(sala);
        exigirPreparacao(partida);
        if (partida != null) {
            if (!partida.getTema().getId().equals(temaId)) {
                throw new RegraTemasException(HttpStatus.CONFLICT, "A sala já tem um tema definido");
            }
            return resposta(codigo, sala.anfitriaoId(), partida);
        }

        return vincularTema(codigo, anfitriao, temaId);
    }

    private void exigirAnfitriao(UUID codigo, Sala sala, Usuario anfitriao) {
        if (!sala.anfitriaoId().equals(anfitriao.getId())) {
            if (!salas.possuiParticipante(codigo, anfitriao.getId())) {
                throw salaNaoEncontrada();
            }
            throw new RegraTemasException(HttpStatus.FORBIDDEN, "Somente o anfitrião pode escolher o tema");
        }
    }

    private Tema temaAprovado(UUID temaId) {
        Tema tema = temas.findById(temaId)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Tema não encontrado"));
        if (tema.getStatus() != StatusTema.APROVADO
                || tema.getCategoria().getStatus() != StatusCategoria.APROVADA) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Tema indisponível para a partida");
        }
        return tema;
    }

    private SalaResponseDTO vincularTema(UUID codigo, Usuario anfitriao, UUID temaId) {
        Partida partida = preparacao.prepararTema(anfitriao, temaAprovado(temaId));
        salas.vincularPartida(codigo, partida.getId());
        return resposta(codigo, anfitriao.getId(), partida);
    }

    @Transactional(readOnly = true)
    public Votacao consultarVotacao(UUID codigo, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        exigirParticipante(codigo, usuarioId);
        Sala sala = salas.buscar(codigo).orElseThrow(this::salaNaoEncontrada);
        return votacao(codigo, usuarioId, sala.votacaoAberta());
    }

    @Transactional
    public Votacao abrirVotacao(UUID codigo, String email) {
        Usuario anfitriao = usuarioAtivo(email);
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirAnfitriao(codigo, sala, anfitriao);
        if (sala.partidaId() != null) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A sala já tem um tema definido");
        }
        salas.definirVotacaoAberta(codigo, true);
        return votacao(codigo, anfitriao.getId(), true);
    }

    @Transactional
    public Votacao registrarVoto(UUID codigo, UUID temaId, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirParticipante(codigo, usuarioId);
        exigirVotacaoAberta(sala);
        if (temaId == null) {
            salas.retirarVoto(codigo, usuarioId);
        } else {
            temaAprovado(temaId);
            salas.votar(codigo, usuarioId, temaId);
        }
        return votacao(codigo, usuarioId, true);
    }

    @Transactional
    public SalaResponseDTO encerrarVotacao(UUID codigo, UUID desempate, String email) {
        Usuario anfitriao = usuarioAtivo(email);
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirAnfitriao(codigo, sala, anfitriao);
        exigirVotacaoAberta(sala);
        List<TotalVotos> totais = salas.contarVotos(codigo);
        if (totais.isEmpty()) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Registre pelo menos um voto válido");
        }
        long maiorTotal = totais.get(0).votos();
        List<UUID> lideres = totais.stream().filter(total -> total.votos() == maiorTotal)
                .map(TotalVotos::temaId).toList();
        UUID vencedor = lideres.size() == 1 ? lideres.get(0) : desempate;
        if (vencedor == null || !lideres.contains(vencedor)) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Escolha um dos temas empatados");
        }
        SalaResponseDTO resultado = vincularTema(codigo, anfitriao, vencedor);
        salas.definirVotacaoAberta(codigo, false);
        return resultado;
    }

    private Votacao votacao(UUID codigo, UUID usuarioId, boolean aberta) {
        return new Votacao(aberta, salas.buscarVoto(codigo, usuarioId).orElse(null), salas.contarVotos(codigo));
    }

    private void exigirVotacaoAberta(Sala sala) {
        if (!sala.votacaoAberta()) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A votação da sala não está aberta");
        }
    }

    private void exigirParticipante(UUID codigo, UUID usuarioId) {
        if (!salas.possuiParticipante(codigo, usuarioId)) {
            throw salaNaoEncontrada();
        }
    }

    @Transactional
    public SalaResponseDTO entrar(UUID codigo, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        Partida partida = bloquearPartida(sala);

        if (!salas.possuiParticipante(codigo, usuarioId)) {
            exigirPreparacao(partida);
            salas.adicionarParticipante(codigo, usuarioId);
        }
        return resposta(codigo, sala.anfitriaoId(), partida);
    }

    @Transactional(readOnly = true)
    public SalaResponseDTO consultar(UUID codigo, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        if (!salas.possuiParticipante(codigo, usuarioId)) {
            throw salaNaoEncontrada();
        }
        Sala sala = salas.buscar(codigo).orElseThrow(this::salaNaoEncontrada);
        Partida partida = sala.partidaId() == null ? null
                : partidas.findById(sala.partidaId()).orElseThrow(this::salaNaoEncontrada);
        return resposta(codigo, sala.anfitriaoId(), partida);
    }

    private Partida bloquearPartida(Sala sala) {
        return sala.partidaId() == null ? null
                : partidas.buscarParaAtualizacao(sala.partidaId()).orElseThrow(this::salaNaoEncontrada);
    }

    private Usuario usuarioAtivo(String email) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.UNAUTHORIZED, "Conta não identificada"));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new RegraTemasException(HttpStatus.FORBIDDEN, "Conta inativa");
        }
        return usuario;
    }

    private void exigirPreparacao(Partida partida) {
        if (partida != null && partida.getStatus() != StatusPartida.PREPARACAO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A partida não está em preparação");
        }
    }

    private RegraTemasException salaNaoEncontrada() {
        return new RegraTemasException(HttpStatus.NOT_FOUND, "Sala não encontrada");
    }

    private SalaResponseDTO resposta(UUID codigo, UUID anfitriaoId, Partida partida) {
        return new SalaResponseDTO(codigo, partida == null ? null : partida.getId(), anfitriaoId,
                partida == null ? StatusPartida.PREPARACAO : partida.getStatus(), salas.listarParticipantes(codigo),
                partida == null ? null : partida.getTema().getId());
    }
}
