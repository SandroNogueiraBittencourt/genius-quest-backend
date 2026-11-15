package br.com.geniusquest.service;

import br.com.geniusquest.dto.pergunta.*;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.validation.PerguntaConteudoValidado;
import br.com.geniusquest.validation.ValidadorPergunta;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class CuradoriaPerguntaService {
    private static final List<StatusPergunta> ATIVAS = List.of(
            StatusPergunta.PENDENTE, StatusPergunta.APROVADA, StatusPergunta.BLOQUEADA);
    private final PerguntaModeracaoRepository moderacao;
    private final PerguntaRepository perguntas;
    private final EventoCuradoriaPerguntaRepository eventos;
    private final UsuarioRepository usuarios;
    private final ValidadorPergunta validador = new ValidadorPergunta();
    private final JsonMapper json;

    public CuradoriaPerguntaService(PerguntaModeracaoRepository moderacao,
            PerguntaRepository perguntas, EventoCuradoriaPerguntaRepository eventos,
            UsuarioRepository usuarios, JsonMapper json) {
        this.moderacao = moderacao;
        this.perguntas = perguntas;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public PaginaPerguntasModeracaoDTO listar(StatusPergunta status, UUID temaId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw erro(HttpStatus.BAD_REQUEST, "Paginação inválida: página >= 0 e tamanho entre 1 e 100.");
        }
        var pageable = PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.asc("criadoEm"), Sort.Order.asc("id")));
        Page<Pergunta> paginaEncontrada;
        if (temaId != null && status != null) {
            paginaEncontrada = moderacao.findByTema_IdAndStatus(temaId, status, pageable);
        } else if (temaId != null) {
            paginaEncontrada = moderacao.findByTema_Id(temaId, pageable);
        } else if (status != null) {
            paginaEncontrada = moderacao.findByStatus(status, pageable);
        } else {
            paginaEncontrada = moderacao.findAll(pageable);
        }
        return new PaginaPerguntasModeracaoDTO(
                paginaEncontrada.getContent().stream().map(this::resposta).toList(),
                new PaginaPerguntasModeracaoDTO.PageInfo(tamanho, pagina,
                        paginaEncontrada.getTotalElements(), paginaEncontrada.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public PerguntaModeracaoDTO buscar(UUID id) {
        return resposta(moderacao.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pergunta não encontrada.")));
    }

    @Transactional(readOnly = true)
    public List<HistoricoCuradoriaDTO> historico(UUID id) {
        if (!moderacao.existsById(id)) {
            throw erro(HttpStatus.NOT_FOUND, "Pergunta não encontrada.");
        }
        return eventos.findByPergunta_IdOrderByCriadoEmAscIdAsc(id).stream()
                .map(e -> new HistoricoCuradoriaDTO(e.getId(), e.getRevisor().getId(),
                        e.getAcao(), e.getStatusAnterior(), e.getStatusNovo(),
                        e.getMotivo(), e.getAntes(), e.getDepois(), e.getCriadoEm()))
                .toList();
    }

    @Transactional
    public PerguntaModeracaoDTO revisar(UUID id, RevisaoPerguntaDTO pedido, String email) {
        Usuario revisor = revisorAtivo(email);
        Pergunta pergunta = bloqueada(id);
        if (pergunta.getStatus() != StatusPergunta.PENDENTE) {
            throw erro(HttpStatus.CONFLICT, "Somente perguntas pendentes podem ser editadas.");
        }
        PerguntaConteudoValidado conteudo;
        try {
            conteudo = validador.validar(pedido.enunciado(), pedido.alternativas(),
                    pedido.respostaCorretaIndice());
        } catch (IllegalArgumentException ex) {
            throw erro(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Pergunta não passou na validação estrutural.");
        }
        if (!conteudo.enunciadoNormalizado().equals(pergunta.getEnunciadoNormalizado())
                && perguntas.existsByTema_IdAndEnunciadoNormalizadoAndStatusIn(
                        pergunta.getTema().getId(), conteudo.enunciadoNormalizado(), ATIVAS)) {
            throw erro(HttpStatus.CONFLICT, "Já existe uma pergunta ativa com o mesmo enunciado.");
        }
        if (pergunta.getAlternativas().size() != 4) {
            throw erro(HttpStatus.CONFLICT, "Pergunta possui alternativas inconsistentes.");
        }
        String antes = snapshot(pergunta);
        StatusPergunta statusAnterior = pergunta.getStatus();
        pergunta.setEnunciado(conteudo.enunciado());
        pergunta.setEnunciadoNormalizado(conteudo.enunciadoNormalizado());
        pergunta.setRespostaCorretaIndice(conteudo.respostaCorretaIndice());
        pergunta.setDificuldadeInicial(pedido.dificuldadeInicial());
        String fonte = pedido.fonteReferencia();
        pergunta.setFonteReferencia(fonte == null || fonte.isBlank() ? null : fonte.strip());
        for (AlternativaPergunta alternativa : pergunta.getAlternativas()) {
            int posicao = alternativa.getPosicao();
            if (posicao < 0 || posicao >= 4) {
                throw erro(HttpStatus.CONFLICT, "Posição de alternativa inconsistente.");
            }
            alternativa.setTexto(conteudo.alternativas().get(posicao));
        }
        pergunta.setRevisor(revisor);
        pergunta.setRevisadoEm(Instant.now());
        pergunta.setMotivoRevisao(pedido.motivo().strip());
        moderacao.saveAndFlush(pergunta);
        registrar(pergunta, revisor, AcaoCuradoriaPergunta.REVISAR, statusAnterior,
                pedido.motivo().strip(), antes);
        return resposta(pergunta);
    }

    @Transactional
    public PerguntaModeracaoDTO decidir(UUID id, DecisaoPerguntaDTO pedido, String email) {
        Usuario revisor = revisorAtivo(email);
        Pergunta pergunta = bloqueada(id);
        AcaoCuradoriaPergunta acao = pedido.acao();
        if (acao == null || acao == AcaoCuradoriaPergunta.REVISAR) {
            throw erro(HttpStatus.BAD_REQUEST, "Ação deve ser APROVAR, REJEITAR ou BLOQUEAR.");
        }
        StatusPergunta anterior = pergunta.getStatus();
        StatusPergunta proximo;
        switch (acao) {
            case APROVAR -> {
                if (anterior != StatusPergunta.PENDENTE) {
                    throw erro(HttpStatus.CONFLICT, "Somente perguntas pendentes podem ser aprovadas.");
                }
                // Valida novamente conteúdo persistido antes de publicar um gabarito.
                validarConteudoPersistido(pergunta);
                proximo = StatusPergunta.APROVADA;
            }
            case REJEITAR -> {
                if (anterior != StatusPergunta.PENDENTE) {
                    throw erro(HttpStatus.CONFLICT, "Somente perguntas pendentes podem ser rejeitadas.");
                }
                proximo = StatusPergunta.REJEITADA;
            }
            case BLOQUEAR -> {
                if (anterior != StatusPergunta.PENDENTE && anterior != StatusPergunta.APROVADA) {
                    throw erro(HttpStatus.CONFLICT, "Somente perguntas pendentes ou aprovadas podem ser bloqueadas.");
                }
                proximo = StatusPergunta.BLOQUEADA;
            }
            default -> throw erro(HttpStatus.BAD_REQUEST, "Ação inválida.");
        }
        String motivo = pedido.motivo() == null ? null : pedido.motivo().strip();
        if ((acao == AcaoCuradoriaPergunta.REJEITAR || acao == AcaoCuradoriaPergunta.BLOQUEAR)
                && (motivo == null || motivo.isBlank())) {
            throw erro(HttpStatus.BAD_REQUEST, "Motivo obrigatório para rejeitar ou bloquear.");
        }
        if (motivo != null && motivo.length() > 500) {
            throw erro(HttpStatus.BAD_REQUEST, "Motivo excede 500 caracteres.");
        }
        String antes = snapshot(pergunta);
        pergunta.setStatus(proximo);
        pergunta.setRevisor(revisor);
        pergunta.setRevisadoEm(Instant.now());
        pergunta.setMotivoRevisao(motivo == null || motivo.isBlank() ? null : motivo);
        moderacao.saveAndFlush(pergunta);
        registrar(pergunta, revisor, acao, anterior, pergunta.getMotivoRevisao(), antes);
        return resposta(pergunta);
    }

    private void validarConteudoPersistido(Pergunta pergunta) {
        try {
            validador.validar(pergunta.getEnunciado(),
                    pergunta.getAlternativas().stream()
                            .sorted(Comparator.comparingInt(AlternativaPergunta::getPosicao))
                            .map(AlternativaPergunta::getTexto).toList(),
                    pergunta.getRespostaCorretaIndice());
        } catch (IllegalArgumentException ex) {
            throw erro(HttpStatus.CONFLICT, "Corrija o conteúdo antes de aprovar a pergunta.");
        }
    }

    private Usuario revisorAtivo(String email) {
        Usuario revisor = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Conta não identificada."));
        if (!Boolean.TRUE.equals(revisor.getAtivo()) || revisor.getPerfil() != PerfilUsuario.ADMIN) {
            throw erro(HttpStatus.FORBIDDEN, "Apenas administrador ativo pode realizar curadoria.");
        }
        return revisor;
    }

    private Pergunta bloqueada(UUID id) {
        return moderacao.buscarParaAlterar(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pergunta não encontrada."));
    }

    private PerguntaModeracaoDTO resposta(Pergunta p) {
        List<String> alternativas = p.getAlternativas().stream()
                .sorted(Comparator.comparingInt(AlternativaPergunta::getPosicao))
                .map(AlternativaPergunta::getTexto).toList();
        return new PerguntaModeracaoDTO(p.getId(), p.getTema().getId(),
                p.getEnunciado(), alternativas, p.getRespostaCorretaIndice(),
                p.getStatus(), p.getOrigem(), p.getDificuldadeInicial(), p.getFonteReferencia(),
                p.getModeloIa(), p.getRevisor() == null ? null : p.getRevisor().getId(),
                p.getMotivoRevisao(), p.getRevisadoEm(), p.getCriadoEm());
    }

    private String snapshot(Pergunta p) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("status", p.getStatus().name());
        dados.put("enunciado", p.getEnunciado());
        dados.put("alternativas", p.getAlternativas().stream()
                .sorted(Comparator.comparingInt(AlternativaPergunta::getPosicao))
                .map(AlternativaPergunta::getTexto).toList());
        dados.put("respostaCorretaIndice", p.getRespostaCorretaIndice());
        dados.put("dificuldadeInicial", p.getDificuldadeInicial().name());
        dados.put("fonteReferencia", p.getFonteReferencia());
        try {
            return json.writeValueAsString(dados);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Falha ao serializar auditoria.", ex);
        }
    }

    private void registrar(Pergunta p, Usuario revisor, AcaoCuradoriaPergunta acao,
            StatusPergunta anterior, String motivo, String antes) {
        EventoCuradoriaPergunta evento = new EventoCuradoriaPergunta();
        evento.setPergunta(p);
        evento.setRevisor(revisor);
        evento.setAcao(acao);
        evento.setStatusAnterior(anterior);
        evento.setStatusNovo(p.getStatus());
        evento.setMotivo(motivo);
        evento.setAntes(antes);
        evento.setDepois(snapshot(p));
        eventos.saveAndFlush(evento);
    }

    private RegraTemasException erro(HttpStatus status, String mensagem) {
        return new RegraTemasException(status, mensagem);
    }
}
