package br.com.geniusquest.service;

import br.com.geniusquest.dto.tema.*;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VotacaoService {
    private final CicloVotacaoRepository ciclos;
    private final TemaRepository temas;
    private final UsuarioRepository usuarios;
    private final VotoTemaRepository votos;

    public VotacaoService(CicloVotacaoRepository ciclos, TemaRepository temas,
            UsuarioRepository usuarios, VotoTemaRepository votos) {
        this.ciclos = ciclos;
        this.temas = temas;
        this.usuarios = usuarios;
        this.votos = votos;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CicloResponseDTO criarCiclo(CicloCadastroDTO dto) {
        if (!dto.fimEm().isAfter(dto.inicioEm()) || !dto.fimEm().isAfter(Instant.now())) {
            throw new RegraTemasException(HttpStatus.BAD_REQUEST, "Período de votação inválido");
        }
        CicloVotacao ciclo = new CicloVotacao();
        ciclo.setNome(dto.nome().strip());
        ciclo.setInicioEm(dto.inicioEm());
        ciclo.setFimEm(dto.fimEm());
        try {
            return response(ciclos.saveAndFlush(ciclo));
        } catch (DataIntegrityViolationException ex) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Existe um ciclo aberto sobreposto");
        }
    }

    @Transactional(readOnly = true)
    public CicloResponseDTO cicloAtual() {
        return response(encontrarAtual());
    }

    @Transactional
    public VotoResponseDTO votar(UUID temaId, String email) {
        // Bloquear a linha do ciclo impede voto concorrente com seu encerramento.
        CicloVotacao ativo = encontrarAtual();
        CicloVotacao ciclo = ciclos.buscarParaEncerramento(ativo.getId())
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Ciclo não encontrado"));
        Instant agora = Instant.now();
        if (ciclo.getStatus() != StatusCicloVotacao.ABERTO
                || agora.isBefore(ciclo.getInicioEm()) || !agora.isBefore(ciclo.getFimEm())) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Ciclo encerrado ou fora do período");
        }

        Usuario usuario = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.UNAUTHORIZED, "Conta não identificada"));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new RegraTemasException(HttpStatus.FORBIDDEN, "Conta inativa");
        }
        Tema tema = temas.findById(temaId)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Tema não encontrado"));
        if (tema.getStatus() != StatusTema.APROVADO
                || tema.getCategoria().getStatus() != StatusCategoria.APROVADA) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Tema não está elegível para votação");
        }
        if (votos.existsByUsuario_IdAndTema_IdAndCiclo_Id(usuario.getId(), temaId, ciclo.getId())) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Voto já registrado neste tema e ciclo");
        }

        VotoTema voto = new VotoTema();
        voto.setTema(tema);
        voto.setUsuario(usuario);
        voto.setCiclo(ciclo);
        try {
            VotoTema salvo = votos.saveAndFlush(voto);
            return new VotoResponseDTO(salvo.getId(), temaId, ciclo.getId(), salvo.getCriadoEm());
        } catch (DataIntegrityViolationException ex) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Voto já registrado neste tema e ciclo");
        }
    }

    @Transactional(readOnly = true)
    public List<TemaRankingDTO> ranking(UUID cicloId) {
        CicloVotacao ciclo = ciclos.findById(cicloId)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Ciclo não encontrado"));
        return montarRanking(ciclo);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CicloResponseDTO encerrar(UUID cicloId) {
        CicloVotacao ciclo = ciclos.buscarParaEncerramento(cicloId)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Ciclo não encontrado"));
        if (ciclo.getStatus() != StatusCicloVotacao.ABERTO) {
            throw new RegraTemasException(HttpStatus.CONFLICT, "Ciclo já encerrado");
        }
        List<TemaRankingDTO> ranking = montarRanking(ciclo);
        if (!ranking.isEmpty() && ranking.get(0).votos() > 0) {
            Tema vencedor = temas.findById(ranking.get(0).temaId()).orElseThrow();
            ciclo.setVencedor(vencedor);
        }
        ciclo.setStatus(StatusCicloVotacao.ENCERRADO);
        ciclo.setEncerradoEm(Instant.now());
        return response(ciclos.save(ciclo));
    }

    private CicloVotacao encontrarAtual() {
        Instant agora = Instant.now();
        return ciclos.findFirstByStatusAndInicioEmLessThanEqualAndFimEmGreaterThanOrderByInicioEmAsc(
                        StatusCicloVotacao.ABERTO, agora, agora)
                .orElseThrow(() -> new RegraTemasException(HttpStatus.NOT_FOUND, "Não há ciclo de votação ativo"));
    }

    private List<TemaRankingDTO> montarRanking(CicloVotacao ciclo) {
        Map<UUID, Long> contagens = votos.contarPorTemaNoCiclo(ciclo.getId()).stream()
                .collect(Collectors.toMap(VotoTemaRepository.ContagemTema::getTemaId,
                        VotoTemaRepository.ContagemTema::getTotal));
        List<Tema> elegiveis = temas.findByStatusOrderByCriadoEmAsc(StatusTema.APROVADO).stream()
                .filter(tema -> tema.getCategoria().getStatus() == StatusCategoria.APROVADA)
                .sorted(Comparator.comparing(Tema::getCriadoEm).thenComparing(Tema::getId))
                .toList();
        List<Tema> ordenados = new ArrayList<>(elegiveis);
        ordenados.sort(Comparator.<Tema>comparingLong(t -> contagens.getOrDefault(t.getId(), 0L))
                .reversed().thenComparing(Tema::getCriadoEm).thenComparing(Tema::getId));
        List<TemaRankingDTO> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Tema tema = ordenados.get(i);
            long total = contagens.getOrDefault(tema.getId(), 0L);
            // 1 voto por usuário/tema/ciclo: votantes distintos == total de votos.
            ranking.add(new TemaRankingDTO(i + 1, tema.getId(), tema.getTitulo(), total, total));
        }
        return ranking;
    }

    private CicloResponseDTO response(CicloVotacao ciclo) {
        return new CicloResponseDTO(ciclo.getId(), ciclo.getNome(), ciclo.getInicioEm(),
                ciclo.getFimEm(), ciclo.getStatus(),
                ciclo.getVencedor() == null ? null : ciclo.getVencedor().getId(), ciclo.getEncerradoEm());
    }
}
