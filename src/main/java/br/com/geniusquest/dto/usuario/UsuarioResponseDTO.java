package br.com.geniusquest.dto.usuario;

import br.com.geniusquest.entity.PerfilUsuario;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponseDTO(

        UUID id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Integer moedas,
        Boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm

) {
}
