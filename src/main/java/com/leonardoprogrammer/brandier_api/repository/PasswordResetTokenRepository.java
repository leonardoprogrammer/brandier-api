package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId);

    @Modifying
    @Query("delete from PasswordResetToken t where t.usuario.id = :usuarioId")
    int deleteAllByUsuario(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsedIfUnused(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :limit")
    int deleteExpired(@Param("limit") Instant limit);
}
