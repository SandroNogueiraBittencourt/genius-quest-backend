package br.com.geniusquest.dto.auth;

public record LoginResponseDTO(

        String token,
        String tipo,
        long expiraEm

) {
}
