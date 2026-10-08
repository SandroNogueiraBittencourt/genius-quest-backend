package br.com.geniusquest.service;

import br.com.geniusquest.dto.usuario.UsuarioAtualizacaoDTO;
import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.entity.PerfilUsuario;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.EmailJaCadastradoException;
import br.com.geniusquest.exception.UsuarioNaoEncontradoException;
import br.com.geniusquest.mapper.UsuarioMapper;
import br.com.geniusquest.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private UsuarioMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService service;

    private UUID usuarioId;
    private Usuario usuario;

    @BeforeEach
    void setUp() {

        usuarioId = UUID.randomUUID();

        usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setNome("Sandro Nogueira");
        usuario.setEmail("sandro@geniusquest.com");
        usuario.setSenha("$2a$10$hash");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        usuario.setMoedas(0);
        usuario.setAtivo(true);
    }

    @Test
    void deveCadastrarUsuarioComSucesso() {

        UsuarioCadastroDTO dto =
                new UsuarioCadastroDTO(
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        "SenhaSegura123!"
                );

        UsuarioResponseDTO resposta =
                new UsuarioResponseDTO(
                        usuarioId,
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        PerfilUsuario.USUARIO,
                        0,
                        true,
                        Instant.now(),
                        Instant.now()
                );

        when(repository.existsByEmailIgnoreCase(
                "sandro@geniusquest.com"
        )).thenReturn(false);

        when(mapper.toEntity(dto))
                .thenReturn(usuario);

        when(passwordEncoder.encode("SenhaSegura123!"))
                .thenReturn("$2a$10$hash");

        when(repository.save(any(Usuario.class)))
                .thenReturn(usuario);

        when(mapper.toResponse(usuario))
                .thenReturn(resposta);

        UsuarioResponseDTO resultado =
                service.cadastrar(dto);

        assertNotNull(resultado);
        assertEquals(usuarioId, resultado.id());
        assertEquals(
                "sandro@geniusquest.com",
                resultado.email()
        );

        verify(repository)
                .existsByEmailIgnoreCase(
                        "sandro@geniusquest.com"
                );

        verify(passwordEncoder)
                .encode("SenhaSegura123!");

        verify(repository)
                .save(usuario);
    }

    @Test
    void naoDeveCadastrarUsuarioComEmailDuplicado() {

        UsuarioCadastroDTO dto =
                new UsuarioCadastroDTO(
                        "Sandro Nogueira",
                        "sandro@geniusquest.com",
                        "SenhaSegura123!"
                );

        when(repository.existsByEmailIgnoreCase(
                "sandro@geniusquest.com"
        )).thenReturn(true);

        assertThrows(
                EmailJaCadastradoException.class,
                () -> service.cadastrar(dto)
        );

        verify(repository, never())
                .save(any());

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    @Test
    void deveBuscarUsuarioPorId() {

        UsuarioResponseDTO resposta =
                new UsuarioResponseDTO(
                        usuarioId,
                        usuario.getNome(),
                        usuario.getEmail(),
                        PerfilUsuario.USUARIO,
                        0,
                        true,
                        Instant.now(),
                        Instant.now()
                );

        when(repository.findById(usuarioId))
                .thenReturn(Optional.of(usuario));

        when(mapper.toResponse(usuario))
                .thenReturn(resposta);

        UsuarioResponseDTO resultado =
                service.buscarPorId(usuarioId);

        assertNotNull(resultado);
        assertEquals(usuarioId, resultado.id());
        assertEquals(
                "Sandro Nogueira",
                resultado.nome()
        );
    }

    @Test
    void deveLancarExcecaoQuandoUsuarioNaoExistir() {

        when(repository.findById(usuarioId))
                .thenReturn(Optional.empty());

        assertThrows(
                UsuarioNaoEncontradoException.class,
                () -> service.buscarPorId(usuarioId)
        );

        verify(mapper, never())
                .toResponse(any());
    }

    @Test
    void deveAtualizarNomeDoUsuario() {

        UsuarioAtualizacaoDTO dto =
                new UsuarioAtualizacaoDTO(
                        "Sandro Nogueira Bittencourt",
                        null
                );

        when(repository.findById(usuarioId))
                .thenReturn(Optional.of(usuario));

        when(mapper.toResponse(usuario))
                .thenAnswer(invocation -> {

                    Usuario u = invocation.getArgument(0);

                    return new UsuarioResponseDTO(
                            u.getId(),
                            u.getNome(),
                            u.getEmail(),
                            u.getPerfil(),
                            u.getMoedas(),
                            u.getAtivo(),
                            Instant.now(),
                            Instant.now()
                    );
                });

        UsuarioResponseDTO resultado =
                service.atualizar(usuarioId, dto);

        assertEquals(
                "Sandro Nogueira Bittencourt",
                resultado.nome()
        );
    }

    @Test
    void naoDeveAtualizarParaEmailJaUtilizado() {

        UUID outroUsuarioId = UUID.randomUUID();

        Usuario outroUsuario = new Usuario();
        outroUsuario.setId(outroUsuarioId);
        outroUsuario.setEmail(
                "outro@geniusquest.com"
        );

        UsuarioAtualizacaoDTO dto =
                new UsuarioAtualizacaoDTO(
                        null,
                        "outro@geniusquest.com"
                );

        when(repository.findById(usuarioId))
                .thenReturn(Optional.of(usuario));

        when(repository.findByEmailIgnoreCase(
                "outro@geniusquest.com"
        )).thenReturn(Optional.of(outroUsuario));

        assertThrows(
                EmailJaCadastradoException.class,
                () -> service.atualizar(usuarioId, dto)
        );
    }
}
