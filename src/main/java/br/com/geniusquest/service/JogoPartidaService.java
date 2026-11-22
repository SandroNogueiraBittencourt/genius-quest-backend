package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.PartidaResponseDTO;
import br.com.geniusquest.dto.partida.RespostaPartidaCadastroDTO;
import br.com.geniusquest.dto.partida.RespostaPartidaDTO;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JogoPartidaService {
    private final PartidaRepository partidas;
    private final PerguntaRepository perguntas;
    private final PerguntaPartidaRepository perguntasPartida;
    private final RespostaPartidaRepository respostas;
    private final UsuarioRepository usuarios;

    public JogoPartidaService(PartidaRepository partidas, PerguntaRepository perguntas,
            PerguntaPartidaRepository perguntasPartida, RespostaPartidaRepository respostas,
            UsuarioRepository usuarios) {
        this.partidas = partidas;
        this.perguntas = perguntas;
        this.perguntasPartida = perguntasPartida;
        this.respostas = respostas;
        this.usuarios = usuarios;
    }

    /**
     * Um lock por partida serializa início e respostas; a transação congela todo o
     * enunciado, as quatro alternativas e o gabarito de uma só vez.
     */
    @Transactional
    public PartidaResponseDTO iniciar(UUID partidaId, String email) {
        Usuario usuario = usuarioAtivo(email);
        Partida partida = partidaDoCriadorComBloqueio(partidaId, usuario);
        if (partida.getStatus() != StatusPartida.PREPARACAO) {
            throw erro(HttpStatus.CONFLICT, "Partida já iniciada ou não está em preparação");
        }

        List<Pergunta> elegiveis = perguntas.findTop10ByTema_IdAndStatusOrderByCriadoEmAscIdAsc(
                partida.getTema().getId(), StatusPergunta.APROVADA);
        if (elegiveis.isEmpty()) {
            throw erro(HttpStatus.CONFLICT, "O tema vencedor ainda não possui perguntas aprovadas");
        }

        List<PerguntaPartida> congeladas = new ArrayList<>();
        for (int ordem = 0; ordem < elegiveis.size(); ordem++) {
            Pergunta pergunta = elegiveis.get(ordem);
            List<AlternativaPergunta> alternativas = pergunta.getAlternativas().stream()
                    .sorted(Comparator.comparingInt(AlternativaPergunta::getPosicao))
                    .toList();
            if (alternativas.size() != 4
                    || pergunta.getRespostaCorretaIndice() < 0
                    || pergunta.getRespostaCorretaIndice() > 3) {
                throw erro(HttpStatus.CONFLICT, "Pergunta aprovada com dados inconsistentes");
            }
            for (int posicao = 0; posicao < 4; posicao++) {
                if (alternativas.get(posicao).getPosicao() != posicao) {
                    throw erro(HttpStatus.CONFLICT, "Posições de alternativas inconsistentes");
                }
            }
            PerguntaPartida snapshot = new PerguntaPartida();
            snapshot.setPartida(partida);
            snapshot.setPergunta(pergunta);
            snapshot.setOrdem(ordem);
            snapshot.setEnunciado(pergunta.getEnunciado());
            snapshot.setAlternativas(alternativas.stream().map(AlternativaPergunta::getTexto).toList());
            snapshot.setRespostaCorretaIndice(pergunta.getRespostaCorretaIndice());
            snapshot.setDificuldadeInicial(pergunta.getDificuldadeInicial());
            congeladas.add(snapshot);
        }

        perguntasPartida.saveAllAndFlush(congeladas);
        partida.setStatus(StatusPartida.EM_ANDAMENTO);
        partidas.saveAndFlush(partida);

        return new PartidaResponseDTO(partida.getId(),
                partida.getCiclo().getId(), partida.getCiclo().getNome(),
                partida.getTema().getId(), partida.getTemaTitulo(),
                partida.getCategoriaNome(), partida.getStatus(), partida.getCriadoEm());
    }

    /**
     * Uma tentativa por pergunta do próprio criador. A decisão de acerto é feita
     * com o gabarito congelado no servidor, jamais com dados vindos do navegador.
     */
    @Transactional
    public RespostaPartidaDTO responder(UUID partidaId, RespostaPartidaCadastroDTO pedido, String email) {
        Usuario usuario = usuarioAtivo(email);
        Partida partida = partidaDoCriadorComBloqueio(partidaId, usuario);
        if (partida.getStatus() != StatusPartida.EM_ANDAMENTO) {
            throw erro(HttpStatus.CONFLICT, "Partida não está em andamento");
        }
        PerguntaPartida snapshot = perguntasPartida.findByPartida_IdAndPergunta_Id(
                        partida.getId(), pedido.perguntaId())
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pergunta não pertence à partida"));

        if (respostas.existsByPerguntaPartida_IdAndUsuario_Id(snapshot.getId(), usuario.getId())) {
            throw erro(HttpStatus.CONFLICT, "Pergunta já respondida nesta partida");
        }

        RespostaPartida resposta = new RespostaPartida();
        resposta.setPerguntaPartida(snapshot);
        resposta.setUsuario(usuario);
        resposta.setRespostaIndice(pedido.alternativaIndice());
        resposta.setCorreta(pedido.alternativaIndice() == snapshot.getRespostaCorretaIndice());
        RespostaPartida salva = respostas.saveAndFlush(resposta);
        return dto(salva);
    }

    @Transactional(readOnly = true)
    public List<RespostaPartidaDTO> minhasRespostas(UUID partidaId, String email) {
        Usuario usuario = usuarioAtivo(email);
        Partida partida = partidas.findById(partidaId)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Partida não encontrada"));
        validarCriador(partida, usuario);
        return respostas.findByPerguntaPartida_Partida_IdAndUsuario_IdOrderByRespondidaEmAscIdAsc(
                        partida.getId(), usuario.getId())
                .stream().map(this::dto).toList();
    }

    private RespostaPartidaDTO dto(RespostaPartida resposta) {
        return new RespostaPartidaDTO(
                resposta.getPerguntaPartida().getPergunta().getId(),
                resposta.getRespostaIndice(), resposta.isCorreta(), resposta.getRespondidaEm());
    }

    private Partida partidaDoCriadorComBloqueio(UUID id, Usuario usuario) {
        Partida partida = partidas.findByIdParaAtualizacao(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Partida não encontrada"));
        validarCriador(partida, usuario);
        return partida;
    }

    private void validarCriador(Partida partida, Usuario usuario) {
        if (!partida.getCriador().getId().equals(usuario.getId())) {
            throw erro(HttpStatus.NOT_FOUND, "Partida não encontrada");
        }
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
}
