package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.usuario.id = :usuarioId and t.revokedAt is null")
    int revokeAllByUsuario(@Param("usuarioId") UUID usuarioId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :limit")
    int deleteExpired(@Param("limit") Instant limit);
}
