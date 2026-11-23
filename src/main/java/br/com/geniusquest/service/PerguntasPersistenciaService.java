package br.com.geniusquest.service;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.PerguntaRepository;
import br.com.geniusquest.repository.TemaRepository;
import br.com.geniusquest.service.ia.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transações curtas: chamadas HTTP ao provedor ficam fora do banco. */
@Service
public class PerguntasPersistenciaService {
    private static final int MAX_ENUNCIADOS_CONTEXTO = 20;
    private static final List<StatusPergunta> ATIVAS = List.of(
            StatusPergunta.PENDENTE, StatusPergunta.APROVADA, StatusPergunta.BLOQUEADA);
    private final TemaRepository temas;
    private final PerguntaRepository perguntas;
    private final ValidadorPergunta validador = new ValidadorPergunta();

    public PerguntasPersistenciaService(TemaRepository temas, PerguntaRepository perguntas) {
        this.temas = temas;
        this.perguntas = perguntas;
    }

    @Transactional(readOnly = true)
    public void validarTemaAprovado(UUID temaId) {
        temaAprovado(temaId);
    }

    @Transactional(readOnly = true)
    public ContextoGeracaoIA contexto(UUID temaId) {
        Tema tema = temaAprovado(temaId);
        List<String> enunciados = perguntas.buscarEnunciadosRecentes(
                temaId, ATIVAS, PageRequest.of(0, MAX_ENUNCIADOS_CONTEXTO));
        return new ContextoGeracaoIA(
                tema.getId(), tema.getTitulo(), tema.getCategoria().getNome(), enunciados);
    }

    @Transactional
    public List<UUID> salvarCandidatas(UUID temaId, LoteGeradoIA lote, int quantidade) {
        if (lote == null || lote.perguntas() == null || lote.perguntas().size() != quantidade
                || quantidade < 1 || quantidade > 10 || lote.modelo() == null
                || lote.modelo().isBlank() || lote.modelo().length() > 100) {
            throw new RegraTemasException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Lote de perguntas IA inválido.");
        }
        Tema tema = temaAprovado(temaId); // Revalida estado após chamada HTTP externa.
        Set<String> recebidas = new HashSet<>();
        List<Pergunta> novas = new ArrayList<>();
        for (CandidataGeradaIA candidata : lote.perguntas()) {
            if (candidata == null || candidata.dificuldade() == null) {
                throw new RegraTemasException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Candidata IA sem dificuldade ou conteúdo.");
            }
            var conteudo = validar(candidata);
            if (!recebidas.add(conteudo.enunciadoNormalizado())
                    || perguntas.existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
                            temaId, conteudo.enunciadoNormalizado(), ATIVAS)) {
                throw new RegraTemasException(HttpStatus.CONFLICT,
                        "Enunciado duplicado para o tema.");
            }
            String fonte = candidata.fonteReferencia();
            if (fonte != null) {
                fonte = fonte.strip();
                if (fonte.length() > 500) {
                    throw new RegraTemasException(HttpStatus.UNPROCESSABLE_CONTENT,
                            "Fonte de referência muito longa.");
                }
            }
            Pergunta pergunta = new Pergunta();
            pergunta.setTema(tema);
            pergunta.setOrigem(OrigemPergunta.IA);
            pergunta.setStatus(StatusPergunta.PENDENTE);
            pergunta.setDificuldadeInicial(candidata.dificuldade());
            pergunta.setEnunciado(conteudo.enunciado());
            pergunta.setEnunciadoNormalizado(conteudo.enunciadoNormalizado());
            pergunta.setRespostaCorretaIndice(conteudo.respostaCorretaIndice());
            pergunta.setFonteReferencia(fonte == null || fonte.isBlank() ? null : fonte);
            pergunta.setModeloIa(lote.modelo().strip());
            pergunta.setVersaoGeracao("quiz-ia-v1");
            for (int i = 0; i < conteudo.alternativas().size(); i++) {
                pergunta.adicionarAlternativa(i, conteudo.alternativas().get(i));
            }
            novas.add(pergunta);
        }
        return perguntas.saveAllAndFlush(novas).stream().map(Pergunta::getId).toList();
    }

    private br.com.geniusquest.validation.PerguntaConteudoValidado validar(CandidataGeradaIA candidata) {
        try {
            return validador.validar(candidata.enunciado(), candidata.alternativas(),
                    candidata.respostaCorretaIndice());
        } catch (IllegalArgumentException ex) {
            throw new RegraTemasException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Pergunta gerada não passou na validação estrutural.");
        }
    }

    private Tema temaAprovado(UUID temaId) {
        Tema tema = temas.findById(temaId)
                .orElseThrow(() -> new RegraTemasException(
                        HttpStatus.NOT_FOUND, "Tema não encontrado."));
        if (tema.getStatus() != StatusTema.APROVADO) {
            throw new RegraTemasException(HttpStatus.CONFLICT,
                    "A geração de perguntas exige tema aprovado.");
        }
        return tema;
    }
}
