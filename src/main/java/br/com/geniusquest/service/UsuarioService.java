package br.com.geniusquest.service;

import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.dto.usuario.UsuarioAtualizacaoDTO;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.EmailJaCadastradoException;
import br.com.geniusquest.mapper.UsuarioMapper;
import br.com.geniusquest.repository.UsuarioRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geniusquest.exception.UsuarioNaoEncontradoException;

import java.util.List;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(
            UsuarioRepository repository,
            UsuarioMapper mapper,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dto) {

        String email = dto.email()
                .trim()
                .toLowerCase();

        if (repository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario = mapper.toEntity(dto);

        usuario.setNome(dto.nome().trim());
        usuario.setEmail(email);

        usuario.setSenha(
                passwordEncoder.encode(dto.senha()));

        Usuario usuarioSalvo = repository.save(usuario);

        return mapper.toResponse(usuarioSalvo);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(
            UUID id,
            UsuarioAtualizacaoDTO dto) {

        Usuario usuario = repository.findById(id)
                .orElseThrow(UsuarioNaoEncontradoException::new);

        if (dto.nome() != null) {
            usuario.setNome(
                    dto.nome().trim());
        }

        if (dto.email() != null) {

            String novoEmail = dto.email()
                    .trim()
                    .toLowerCase();

            repository.findByEmailIgnoreCase(novoEmail)
                    .filter(usuarioExistente -> !usuarioExistente.getId().equals(id))
                    .ifPresent(usuarioExistente -> {
                        throw new EmailJaCadastradoException();
                    });

            usuario.setEmail(novoEmail);
        }

        return mapper.toResponse(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {

        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id) {

        Usuario usuario = repository.findById(id)
                .orElseThrow(UsuarioNaoEncontradoException::new);

        return mapper.toResponse(usuario);
    }
}
