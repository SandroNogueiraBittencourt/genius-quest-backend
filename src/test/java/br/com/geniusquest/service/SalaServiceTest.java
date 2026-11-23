package br.com.geniusquest.service;

import br.com.geniusquest.dto.partida.PerguntaPartidaDTO;
import br.com.geniusquest.dto.partida.SalaResponseDTO.Participante;
import br.com.geniusquest.entity.Partida;
import br.com.geniusquest.entity.StatusPartida;
import br.com.geniusquest.entity.StatusTema;
import br.com.geniusquest.entity.Tema;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.RegraTemasException;
import br.com.geniusquest.repository.PartidaPerguntaRepository;
import br.com.geniusquest.repository.PartidaRepository;
import br.com.geniusquest.repository.SalaRepository;
import br.com.geniusquest.repository.SalaRepository.Sala;
import br.com.geniusquest.repository.TemaRepository;
import br.com.geniusquest.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaServiceTest {
    @Mock private SalaRepository salas;
    @Mock private PartidaRepository partidas;
    @Mock private UsuarioRepository usuarios;
    @Mock private TemaRepository temas;
    @Mock private PartidaService preparacao;
    @Mock private PartidaPerguntaRepository perguntasDaPartida;
    @Mock private Partida partida;
    @Mock private Tema tema;
    @InjectMocks private SalaService service;

    private static final String EMAIL = "jogador@geniusquest.test";
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID partidaId = UUID.randomUUID();
    private final UUID codigo = UUID.randomUUID();

    @Test
    void anfitriaoIniciaPartidaAoPrepararTodasAsPerguntas() {
        identificarUsuario(true);
        localizarPartidaDaSala();
        when(partida.getId()).thenReturn(partidaId);
        when(partida.getPerguntaAtual()).thenReturn(1);
        when(partida.getStatus()).thenReturn(StatusPartida.PREPARACAO);
        when(partida.getTema()).thenReturn(tema);
        UUID temaId = UUID.randomUUID();
        when(tema.getId()).thenReturn(temaId);
        when(perguntasDaPartida.copiarPerguntasAprovadas(partidaId, temaId, 10)).thenReturn(10);
        var pergunta = new PerguntaPartidaDTO(partidaId, 1, 10, "Pergunta", List.of("A", "B", "C", "D"));
        when(perguntasDaPartida.buscar(partidaId, 1)).thenReturn(Optional.of(pergunta));

        assertEquals(pergunta, service.iniciarPartida(codigo, EMAIL));
        verify(partida).iniciar();
    }

    @Test
    void naoIniciaPartidaSemTemaDefinido() {
        identificarUsuario(true);
        when(salas.buscarParaAtualizacao(codigo)).thenReturn(Optional.of(new Sala(null, usuarioId)));

        var erro = assertThrows(RegraTemasException.class, () -> service.iniciarPartida(codigo, EMAIL));

        assertEquals(HttpStatus.CONFLICT, erro.getStatus());
        verifyNoInteractions(perguntasDaPartida, partidas);
    }

    @Test
    void criaSalaSemPartidaNemTema() {
        identificarUsuario(true);

        var sala = service.criar(EMAIL);

        assertNull(sala.partidaId());
        assertNull(sala.temaId());
        assertEquals(usuarioId, sala.anfitriaoId());
        verify(salas).criar(sala.codigo(), null, usuarioId);
        verify(salas).adicionarParticipante(sala.codigo(), usuarioId);
        verifyNoInteractions(partidas, temas, preparacao);
    }

    @Test
    void criaSalaEAdicionaCriadorComoAnfitriao() {
        Usuario criador = identificarUsuario(true);
        when(partidas.findByIdParaAtualizacao(partidaId)).thenReturn(Optional.of(partida));
        when(salas.buscarCodigo(partidaId)).thenReturn(Optional.empty());
        when(partida.getCriador()).thenReturn(criador);
        when(partida.getId()).thenReturn(partidaId);
        when(partida.getStatus()).thenReturn(StatusPartida.PREPARACAO);
        when(partida.getTema()).thenReturn(tema);

        var sala = service.criar(partidaId, EMAIL);

        assertEquals(partidaId, sala.partidaId());
        assertEquals(usuarioId, sala.anfitriaoId());
        assertEquals(StatusPartida.PREPARACAO, sala.status());
        verify(salas).criar(sala.codigo(), partidaId, usuarioId);
        verify(salas).adicionarParticipante(sala.codigo(), usuarioId);
    }

    @Test
    void rejeitaTemaPendenteSemPrepararPartida() {
        identificarUsuario(true);
        UUID temaId = UUID.randomUUID();
        when(salas.buscarParaAtualizacao(codigo)).thenReturn(Optional.of(new Sala(null, usuarioId)));
        when(temas.findById(temaId)).thenReturn(Optional.of(tema));
        when(tema.getStatus()).thenReturn(StatusTema.PENDENTE);

        var erro = assertThrows(RegraTemasException.class, () -> service.escolherTema(codigo, temaId, EMAIL));

        assertEquals(HttpStatus.CONFLICT, erro.getStatus());
        verifyNoInteractions(partidas, preparacao);
    }

    @Test
    void impedeCriacaoPorOutraConta() {
        identificarUsuario(true);
        Usuario dono = new Usuario();
        dono.setId(UUID.randomUUID());
        when(partidas.findByIdParaAtualizacao(partidaId)).thenReturn(Optional.of(partida));
        when(partida.getCriador()).thenReturn(dono);

        var erro = assertThrows(RegraTemasException.class, () -> service.criar(partidaId, EMAIL));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatus());
        verifyNoInteractions(salas);
    }

    @Test
    void bloqueiaNovoParticipanteQuandoPartidaJaComecou() {
        identificarUsuario(true);
        localizarPartidaDaSala();
        when(partida.getStatus()).thenReturn(StatusPartida.EM_ANDAMENTO);

        var erro = assertThrows(RegraTemasException.class, () -> service.entrar(codigo, EMAIL));

        assertEquals(HttpStatus.CONFLICT, erro.getStatus());
        verify(salas, never()).adicionarParticipante(codigo, usuarioId);
    }

    @Test
    void permiteRepetirEntradaSemRegistrarOutraParticipacao() {
        identificarUsuario(true);
        localizarPartidaDaSala();
        when(salas.possuiParticipante(codigo, usuarioId)).thenReturn(true);
        when(partida.getTema()).thenReturn(tema);
        when(partida.getId()).thenReturn(partidaId);
        when(partida.getStatus()).thenReturn(StatusPartida.EM_ANDAMENTO);
        var participantes = List.of(new Participante(usuarioId, "Jogador"));
        when(salas.listarParticipantes(codigo)).thenReturn(participantes);

        var sala = service.entrar(codigo, EMAIL);

        assertEquals(codigo, sala.codigo());
        assertEquals(participantes, sala.participantes());
        verify(salas, never()).adicionarParticipante(codigo, usuarioId);
    }

    @Test
    void impedeConsultaPorQuemNaoParticipa() {
        identificarUsuario(true);

        var erro = assertThrows(RegraTemasException.class, () -> service.consultar(codigo, EMAIL));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatus());
        verify(salas, never()).listarParticipantes(codigo);
        verifyNoInteractions(partidas);
    }

    @Test
    void impedeEntradaDeContaInativa() {
        identificarUsuario(false);

        var erro = assertThrows(RegraTemasException.class, () -> service.entrar(codigo, EMAIL));

        assertEquals(HttpStatus.FORBIDDEN, erro.getStatus());
        verifyNoInteractions(salas, partidas);
    }

    private Usuario identificarUsuario(boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setAtivo(ativo);
        when(usuarios.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private void localizarPartidaDaSala() {
        when(salas.buscarParaAtualizacao(codigo)).thenReturn(Optional.of(new Sala(partidaId, usuarioId)));
        when(partidas.findByIdParaAtualizacao(partidaId)).thenReturn(Optional.of(partida));
    }
}
