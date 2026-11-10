package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.*;
import br.com.geniusquest.entity.*;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PartidaService {
    private final PartidaRepository partidas;
    private final CicloVotacaoRepository ciclos;
    private final ResultadoTemaCicloRepository resultados;
    private final UsuarioRepository usuarios;

    public PartidaService(PartidaRepository partidas,
            CicloVotacaoRepository ciclos,
            ResultadoTemaCicloRepository resultados,
            UsuarioRepository usuarios) {
        this.partidas = partidas;
        this.ciclos = ciclos;
        this.resultados = resultados;
        this.usuarios = usuarios;
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

        Partida partida = new Partida();
        partida.setCiclo(ciclo);
        partida.setTema(vencedor);
        partida.setCriador(criador);
        partida.setTemaTitulo(titulo);
        partida.setCategoriaNome(vencedor.getCategoria().getNome());
        return resposta(partidas.saveAndFlush(partida));
    }

    @Transactional(readOnly = true)
    public List<PartidaResponseDTO> minhas(String email) {
        Usuario usuario = usuarioAtivo(email);
        return partidas.findTop20ByCriador_IdOrderByCriadoEmDescIdDesc(usuario.getId())
                .stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public PartidaResponseDTO buscar(UUID id, String email) {
        Usuario usuario = usuarioAtivo(email);
        Partida partida = partidas.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Partida não encontrada"));
        if (!partida.getCriador().getId().equals(usuario.getId())) {
            // Não revelar a existência de partidas particulares de outras contas.
            throw erro(HttpStatus.NOT_FOUND, "Partida não encontrada");
        }
        return resposta(partida);
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
        return new PartidaResponseDTO(partida.getId(),
                partida.getCiclo().getId(), partida.getCiclo().getNome(),
                partida.getTema().getId(), partida.getTemaTitulo(),
                partida.getCategoriaNome(), partida.getStatus(), partida.getCriadoEm());
    }
}
