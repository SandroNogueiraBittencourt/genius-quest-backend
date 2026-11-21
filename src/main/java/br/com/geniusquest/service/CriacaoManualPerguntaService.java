package br.com.geniusquest.service;

import br.com.geniusquest.dto.pergunta.CriacaoPerguntaManualDTO;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.PerguntaRepository;
import br.com.geniusquest.repository.TemaRepository;
import br.com.geniusquest.validation.PerguntaConteudoValidado;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria apenas candidatas manuais: não aprova, não chama IA e não expõe gabarito. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class CriacaoManualPerguntaService {
    private static final List<StatusPergunta> ATIVAS = List.of(
            StatusPergunta.PENDENTE, StatusPergunta.APROVADA, StatusPergunta.BLOQUEADA);

    private final TemaRepository temas;
    private final PerguntaRepository perguntas;
    private final ValidadorPergunta validador = new ValidadorPergunta();

    public CriacaoManualPerguntaService(TemaRepository temas, PerguntaRepository perguntas) {
        this.temas = temas;
        this.perguntas = perguntas;
    }

    @Transactional
    public UUID criar(CriacaoPerguntaManualDTO pedido) {
        Tema tema = temas.findById(pedido.temaId())
                .orElseThrow(() -> new RegraTemasException(
                        HttpStatus.NOT_FOUND, "Tema não encontrado."));
        if (tema.getStatus() != StatusTema.APROVADO) {
            throw new RegraTemasException(
                    HttpStatus.CONFLICT, "Somente temas aprovados podem receber perguntas.");
        }

        PerguntaConteudoValidado conteudo;
        try {
            conteudo = validador.validar(
                    pedido.enunciado(), pedido.alternativas(), pedido.respostaCorretaIndice());
        } catch (IllegalArgumentException ex) {
            throw new RegraTemasException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "Pergunta não passou na validação estrutural.");
        }
        if (perguntas.existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
                tema.getId(), conteudo.enunciadoNormalizado(), ATIVAS)) {
            throw new RegraTemasException(
                    HttpStatus.CONFLICT, "Já existe uma pergunta ativa com o mesmo enunciado.");
        }

        Pergunta candidata = new Pergunta();
        candidata.setTema(tema);
        candidata.setOrigem(OrigemPergunta.MANUAL);
        candidata.setStatus(StatusPergunta.PENDENTE);
        candidata.setEnunciado(conteudo.enunciado());
        candidata.setEnunciadoNormalizado(conteudo.enunciadoNormalizado());
        candidata.setRespostaCorretaIndice(conteudo.respostaCorretaIndice());
        candidata.setDificuldadeInicial(pedido.dificuldadeInicial());
        String fonte = pedido.fonteReferencia();
        candidata.setFonteReferencia(fonte == null || fonte.isBlank() ? null : fonte.strip());

        for (int i = 0; i < conteudo.alternativas().size(); i++) {
            candidata.adicionarAlternativa(i, conteudo.alternativas().get(i));
        }
        return perguntas.saveAndFlush(candidata).getId();
    }
}
