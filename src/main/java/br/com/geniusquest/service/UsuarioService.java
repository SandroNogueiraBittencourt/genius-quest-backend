package br.com.geniusquest.service;

import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.dto.usuario.UsuarioAtualizacaoDTO;
import br.com.geniusquest.entity.Usuario;
import br.com.geniusquest.exception.EmailJaCadastradoException;
import br.com.geniusquest.mapper.UsuarioMapper;
import br.com.geniusquest.repository.UsuarioRepository;
import br.com.geniusquest.repository.TokenRecuperacaoSenhaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final VerificacaoEmailService verificacao;
    private final TokenRecuperacaoSenhaRepository recuperacao;

    public UsuarioService(
            UsuarioRepository repository,
            UsuarioMapper mapper,
            PasswordEncoder passwordEncoder, VerificacaoEmailService verificacao,
            TokenRecuperacaoSenhaRepository recuperacao) {

        this.repository = repository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.verificacao = verificacao;
        this.recuperacao = recuperacao;
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
        usuario.setEmailVerificado(false);

        usuario.setSenha(
                passwordEncoder.encode(dto.senha()));

        Usuario usuarioSalvo = repository.save(usuario);
        repository.flush();
        verificacao.emitir(usuarioSalvo);

        return mapper.toResponse(usuarioSalvo);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or @usuarioAccessPolicy.isOwner(#p0, authentication)")
    public UsuarioResponseDTO atualizar(
            UUID id,
            UsuarioAtualizacaoDTO dto) {

        Usuario usuario = repository.bloquearPorId(id)
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

            if (!novoEmail.equalsIgnoreCase(usuario.getEmail())) {
                usuario.setEmail(novoEmail);
                usuario.setEmailVerificado(false);
                usuario.incrementarVersaoCredenciais();
                repository.flush();
                recuperacao.invalidarTokensAbertos(id);
                verificacao.emitir(usuario);
            }
        }

        return mapper.toResponse(usuario);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponseDTO> listar() {

        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UsuarioResponseDTO> listar(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or @usuarioAccessPolicy.isOwner(#p0, authentication)")
    public UsuarioResponseDTO buscarPorId(UUID id) {

        Usuario usuario = repository.findById(id)
                .orElseThrow(UsuarioNaoEncontradoException::new);

        return mapper.toResponse(usuario);
    }
}
