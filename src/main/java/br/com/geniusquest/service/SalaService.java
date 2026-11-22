package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.ContinuacaoSalaDTO;
import br.com.geniusquest.dto.partida.ContinuacaoSalaDTO.Status;
import br.com.geniusquest.dto.partida.PerguntaPartidaDTO;
import br.com.geniusquest.dto.partida.RespostaSalaDTO;
import br.com.geniusquest.dto.partida.SalaResponseDTO;
import br.com.geniusquest.entity.Partida;
import br.com.geniusquest.entity.StatusCategoria;
import br.com.geniusquest.entity.StatusPartida;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.PartidaRepository;
import br.com.geniusquest.repository.PartidaPerguntaRepository;
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
    private static final int TOTAL_PERGUNTAS = 10;

    public record Votacao(boolean aberta, UUID meuVoto, List<TotalVotos> totais) {}

    private final SalaRepository salas;
    private final PartidaRepository partidas;
    private final UsuarioRepository usuarios;
    private final TemaRepository temas;
    private final PartidaService preparacao;
    private final PartidaPerguntaRepository perguntasDaPartida;

    public SalaService(SalaRepository salas, PartidaRepository partidas, UsuarioRepository usuarios,
            TemaRepository temas, PartidaService preparacao, PartidaPerguntaRepository perguntasDaPartida) {
        this.salas = salas;
        this.partidas = partidas;
        this.usuarios = usuarios;
        this.temas = temas;
        this.preparacao = preparacao;
        this.perguntasDaPartida = perguntasDaPartida;
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
        Partida partida = partidas.findByIdParaAtualizacao(partidaId)
                .filter(p -> p.getCriador().getId().equals(usuarioId))
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Partida não encontrada"));

        var existente = salas.buscarCodigo(partidaId);
        if (existente.isPresent()) {
            return consultar(existente.get(), email);
        }
        exigirPreparacao(partida);
        return resposta(abrirSala(partidaId, usuarioId), usuarioId, partida);
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
        Sala sala = bloquearSalaAberta(codigo);
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
            throw new RegraTemasException(HttpStatus.FORBIDDEN, "Operação reservada ao anfitrião da sala");
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
        Sala sala = bloquearSalaAberta(codigo);
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
        Sala sala = bloquearSalaAberta(codigo);
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
        Sala sala = bloquearSalaAberta(codigo);
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
        Sala sala = bloquearSalaAberta(codigo);
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
        return resposta(codigo, sala.anfitriaoId(), partida, sala.encerrada());
    }

    @Transactional
    public void fecharSala(UUID codigo, String email) {
        Usuario usuario = usuarioAtivo(email);
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirAnfitriao(codigo, sala, usuario);
        if (!sala.encerrada()) fecharSala(codigo, bloquearPartida(sala));
    }

    @Transactional
    public void sair(UUID codigo, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirParticipante(codigo, usuarioId);
        if (sala.encerrada()) return;

        Partida partida = bloquearPartida(sala);
        if (sala.anfitriaoId().equals(usuarioId)) {
            fecharSala(codigo, partida);
            return;
        }
        salas.removerParticipante(codigo, usuarioId);
        if (partida == null) return;
        if (partida.getStatus() == StatusPartida.EM_ANDAMENTO) {
            avancarSeTodosResponderam(codigo, partida, perguntaAtual(partida).totalPerguntas());
        } else if (partida.getStatus() == StatusPartida.CONCLUIDA) {
            ContinuacaoSalaDTO votos = continuacao(codigo, partida.getId(), sala.anfitriaoId());
            if (votos.status() == Status.AGUARDANDO) {
                salas.retirarVotoContinuacao(partida.getId(), usuarioId);
                aplicarDecisao(codigo, partida.getId(), continuacao(codigo, partida.getId(), sala.anfitriaoId()));
            }
        }
    }

    private void fecharSala(UUID codigo, Partida partida) {
        if (partida != null && partida.getStatus() != StatusPartida.CONCLUIDA) {
            partida.cancelar();
        }
        salas.fechar(codigo);
    }

    private Sala bloquearSalaAberta(UUID codigo) {
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        if (sala.encerrada()) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A sala está fechada");
        }
        return sala;
    }

    @Transactional
    public PerguntaPartidaDTO iniciarPartida(UUID codigo, String email) {
        Usuario anfitriao = usuarioAtivo(email);
        Sala sala = bloquearSalaAberta(codigo);
        exigirAnfitriao(codigo, sala, anfitriao);
        Partida partida = bloquearPartida(sala);
        if (partida == null) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Defina o tema da sala antes de iniciar a partida");
        }
        if (partida.getStatus() == StatusPartida.EM_ANDAMENTO) {
            return perguntaAtual(partida);
        }
        exigirPreparacao(partida);
        int copiadas = perguntasDaPartida.copiarPerguntasAprovadas(partida.getId(),
                partida.getTema().getId(), TOTAL_PERGUNTAS);
        if (copiadas != TOTAL_PERGUNTAS) {
            throw new RegraTemasException(HttpStatus.CONFLICT,
                    "São necessárias pelo menos 10 perguntas aprovadas e válidas para iniciar a partida");
        }
        partida.iniciar();
        return perguntaAtual(partida);
    }

    @Transactional(readOnly = true)
    public PerguntaPartidaDTO consultarPerguntaAtual(UUID codigo, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        exigirParticipante(codigo, usuarioId);
        Sala sala = salas.buscar(codigo).orElseThrow(this::salaNaoEncontrada);
        Partida partida = sala.partidaId() == null ? null
                : partidas.findById(sala.partidaId()).orElseThrow(this::salaNaoEncontrada);
        if (partida == null || partida.getStatus() != StatusPartida.EM_ANDAMENTO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A partida ainda não está em andamento");
        }
        return perguntaAtual(partida);
    }

    @Transactional
    public RespostaSalaDTO responder(UUID codigo, int numero, int alternativaIndice, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Sala sala = bloquearSalaAberta(codigo);
        exigirParticipante(codigo, usuarioId);
        Partida partida = bloquearPartida(sala);
        if (partida == null || partida.getStatus() != StatusPartida.EM_ANDAMENTO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A partida não está em andamento");
        }
        if (numero != partida.getPerguntaAtual()) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A resposta precisa ser para a pergunta atual");
        }
        PerguntaPartidaDTO pergunta = perguntaAtual(partida);
        if (!perguntasDaPartida.registrarResposta(partida.getId(), numero, usuarioId, alternativaIndice)) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Você já respondeu a esta pergunta");
        }
        avancarSeTodosResponderam(codigo, partida, pergunta.totalPerguntas());
        return new RespostaSalaDTO(numero, partida.getStatus(),
                partida.getStatus() == StatusPartida.CONCLUIDA ? null : partida.getPerguntaAtual());
    }

    private void avancarSeTodosResponderam(UUID codigo, Partida partida, int totalPerguntas) {
        if (perguntasDaPartida.todosResponderam(codigo, partida.getId(), partida.getPerguntaAtual())) {
            partida.avancarPergunta(totalPerguntas);
        }
    }

    private PerguntaPartidaDTO perguntaAtual(Partida partida) {
        return perguntasDaPartida.buscar(partida.getId(), partida.getPerguntaAtual())
                .orElseThrow(() -> new RegraTemasException(HttpStatus.CONFLICT,
                        "A partida não tem perguntas preparadas"));
    }

    @Transactional(readOnly = true)
    public ContinuacaoSalaDTO consultarContinuacao(UUID codigo, UUID partidaId, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        exigirParticipante(codigo, usuarioId);
        return continuacao(codigo, partidaId, usuarioId);
    }

    @Transactional
    public ContinuacaoSalaDTO votarContinuacao(UUID codigo, UUID partidaId, boolean continuar, String email) {
        UUID usuarioId = usuarioAtivo(email).getId();
        Sala sala = salas.buscarParaAtualizacao(codigo).orElseThrow(this::salaNaoEncontrada);
        exigirParticipante(codigo, usuarioId);
        ContinuacaoSalaDTO atual = continuacao(codigo, partidaId, usuarioId);
        if (Boolean.valueOf(continuar).equals(atual.meuVoto())) {
            return atual;
        }
        if (sala.encerrada() || !partidaId.equals(sala.partidaId()) || atual.status() != Status.AGUARDANDO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "A votação de continuação foi encerrada");
        }
        if (!salas.votarContinuacao(partidaId, usuarioId, continuar)) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Você já votou sobre continuar");
        }

        return aplicarDecisao(codigo, partidaId, continuacao(codigo, partidaId, usuarioId));
    }

    private ContinuacaoSalaDTO aplicarDecisao(UUID codigo, UUID partidaId, ContinuacaoSalaDTO atual) {
        Status decisao = decidirContinuacao(atual);
        if (decisao == Status.AGUARDANDO) {
            return atual;
        }
        salas.decidirContinuacao(partidaId, decisao, atual.totalJogadores());
        if (decisao == Status.APROVADA) {
            salas.reabrirVotacaoDeTema(codigo);
        }
        return new ContinuacaoSalaDTO(decisao, atual.meuVoto(), atual.favoraveis(),
                atual.contrarios(), atual.totalJogadores());
    }

    private ContinuacaoSalaDTO continuacao(UUID codigo, UUID partidaId, UUID usuarioId) {
        return salas.consultarContinuacao(codigo, partidaId, usuarioId)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.CONFLICT,
                        "A continuação só pode ser votada após o fim da partida da sala"));
    }

    private Status decidirContinuacao(ContinuacaoSalaDTO votos) {
        if (votos.favoraveis() > votos.totalJogadores() / 2) {
            return Status.APROVADA;
        }
        if (votos.totalJogadores() - votos.contrarios() <= votos.totalJogadores() / 2) {
            return Status.ENCERRADA;
        }
        return Status.AGUARDANDO;
    }

    private Partida bloquearPartida(Sala sala) {
        return sala.partidaId() == null ? null
                : partidas.findByIdParaAtualizacao(sala.partidaId()).orElseThrow(this::salaNaoEncontrada);
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
        return resposta(codigo, anfitriaoId, partida, false);
    }

    private SalaResponseDTO resposta(UUID codigo, UUID anfitriaoId, Partida partida, boolean encerrada) {
        return new SalaResponseDTO(codigo, partida == null ? null : partida.getId(), anfitriaoId,
                partida == null ? StatusPartida.PREPARACAO : partida.getStatus(), salas.listarParticipantes(codigo),
                partida == null ? null : partida.getTema().getId(), encerrada);
    }
}
