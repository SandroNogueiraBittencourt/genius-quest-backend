package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.*;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PartidaService {
    private final PartidaRepository partidas;
    private final CicloVotacaoRepository ciclos;
    private final ResultadoTemaCicloRepository resultados;
    private final UsuarioRepository usuarios;
    private final PerguntaRepository perguntas;
    private final PerguntaPartidaRepository perguntasPartida;
    private final SalaRepository salas;

    public PartidaService(PartidaRepository partidas,
            CicloVotacaoRepository ciclos,
            ResultadoTemaCicloRepository resultados,
            UsuarioRepository usuarios,
            PerguntaRepository perguntas,
            PerguntaPartidaRepository perguntasPartida,
            SalaRepository salas) {
        this.partidas = partidas;
        this.ciclos = ciclos;
        this.resultados = resultados;
        this.usuarios = usuarios;
        this.perguntas = perguntas;
        this.perguntasPartida = perguntasPartida;
        this.salas = salas;
    }

    @Transactional
    public PartidaResponseDTO preparar(PartidaCadastroDTO pedido, String email) {
        Usuario criador = usuarioAtivo(email);
        CicloVotacao ciclo = ciclos.findById(pedido.cicloId())
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Ciclo não encontrado"));
        if (ciclo.getStatus() != StatusCicloVotacao.ENCERRADO
                || ciclo.getVencedor() == null) {
            throw erro(HttpStatus.CONFLICT,
                    "É necessário um ciclo encerrado com tema vencedor");
        }
        // A decisão sobre o tema vencedor sempre vem do servidor, nunca do cliente.
        Tema vencedor = ciclo.getVencedor();
        String titulo = resultados.findByCicloIdAndTemaId(ciclo.getId(), vencedor.getId())
                .map(ResultadoTemaCiclo::getTituloTema)
                .orElseGet(vencedor::getTitulo);

        return resposta(salvarPartida(criador, ciclo, vencedor, titulo));
    }

    Partida prepararTema(Usuario criador, Tema tema) {
        return salvarPartida(criador, null, tema, tema.getTitulo());
    }

    private Partida salvarPartida(Usuario criador, CicloVotacao ciclo, Tema tema, String titulo) {
        Partida partida = new Partida();
        partida.setCiclo(ciclo);
        partida.setTema(tema);
        partida.setCriador(criador);
        partida.setTemaTitulo(titulo);
        partida.setCategoriaNome(tema.getCategoria().getNome());
        return partidas.saveAndFlush(partida);
    }

    @Transactional(readOnly = true)
    public List<PartidaResponseDTO> minhas(String email, boolean incluirSalas) {
        Usuario usuario = usuarioAtivo(email);
        if (incluirSalas) {
            return partidas.listarMinhasComSalas(usuario.getId()).stream()
                    .map(this::respostaComSala)
                    .toList();
        }
        return partidas.findTop20ByCriador_IdAndCicloIsNotNullOrderByCriadoEmDescIdDesc(usuario.getId())
                .stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public Page<PartidaResponseDTO> minhas(String email, boolean incluirSalas, Pageable pageable) {
        UUID usuarioId = usuarioAtivo(email).getId();
        if (incluirSalas) {
            return partidas.listarMinhasComSalas(usuarioId, pageable).map(this::respostaComSala);
        }
        return partidas.findByCriador_IdAndCicloIsNotNullOrderByCriadoEmDescIdDesc(usuarioId, pageable)
                .map(this::resposta);
    }

    private PartidaResponseDTO respostaComSala(Partida partida) {
        return resposta(partida, salas.buscarCodigo(partida.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public PartidaResponseDTO buscar(UUID id, String email) {
        return resposta(partidaDoCriador(id, email));
    }

    /** Consulta preliminar das questões aprovadas do tema vencedor, sem revelar o gabarito. */
    @Transactional(readOnly = true)
    public List<PerguntaQuizDTO> perguntasElegiveis(UUID partidaId, String email) {
        Partida partida = partidaDoCriador(partidaId, email);
        exigirJogoIndividual(partida);
        if (partida.getStatus() != StatusPartida.PREPARACAO) {
            // Depois do início, as questões e alternativas são lidas exclusivamente
            // do snapshot, independentemente de edições posteriores na curadoria.
            return perguntasPartida.findByPartida_IdOrderByOrdemAsc(partida.getId())
                    .stream().map(snapshot -> new PerguntaQuizDTO(
                            snapshot.getPergunta().getId(), snapshot.getEnunciado(),
                            snapshot.getAlternativas(), snapshot.getDificuldadeInicial()))
                    .toList();
        }
        return perguntas.findTop10ByTema_IdAndStatusOrderByCriadoEmAscIdAsc(
                        partida.getTema().getId(), StatusPergunta.APROVADA)
                .stream().map(pergunta -> new PerguntaQuizDTO(
                        pergunta.getId(),
                        pergunta.getEnunciado(),
                        pergunta.getAlternativas().stream()
                                .sorted(Comparator.comparingInt(AlternativaPergunta::getPosicao))
                                .map(AlternativaPergunta::getTexto).toList(),
                        pergunta.getDificuldadeInicial()))
                .toList();
    }

    void exigirJogoIndividual(Partida partida) {
        if (partida.getCiclo() == null || salas.buscarCodigo(partida.getId()).isPresent()) {
            throw erro(HttpStatus.CONFLICT, "Use a sala para jogar esta partida");
        }
    }

    private Partida partidaDoCriador(UUID id, String email) {
        Usuario usuario = usuarioAtivo(email);
        Partida partida = partidas.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Partida não encontrada"));
        if (!partida.getCriador().getId().equals(usuario.getId())) {
            // Não revelar a existência de partidas particulares de outras contas.
            throw erro(HttpStatus.NOT_FOUND, "Partida não encontrada");
        }
        return partida;
    }

    private Usuario usuarioAtivo(String email) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não identificada"));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw erro(HttpStatus.FORBIDDEN, "Conta inativa");
        }
        return usuario;
    }

    private RegraTemasException erro(HttpStatus status, String mensagem) {
        return new RegraTemasException(status, mensagem);
    }

    private PartidaResponseDTO resposta(Partida partida) {
        return resposta(partida, null);
    }

    private PartidaResponseDTO resposta(Partida partida, UUID codigoSala) {
        CicloVotacao ciclo = partida.getCiclo();
        return new PartidaResponseDTO(partida.getId(),
                ciclo == null ? null : ciclo.getId(), ciclo == null ? null : ciclo.getNome(),
                partida.getTema().getId(), partida.getTemaTitulo(),
                partida.getCategoriaNome(), partida.getStatus(), partida.getCriadoEm(), codigoSala);
    }
}
