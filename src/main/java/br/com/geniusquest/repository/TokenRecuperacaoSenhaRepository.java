package br.com.geniusquest.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.geniusquest.entity.TokenRecuperacaoSenha;

public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenha, UUID>{
    // Bloqueio impede consumo simultaneo do mesmo token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenRecuperacaoSenha> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update TokenRecuperacaoSenha t set t.usado = true where t.usuario.id = :usuarioId and t.usado = false")
    void invalidarTokensAbertos(@Param("usuarioId") UUID usuarioId);
}
