package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.RefreshToken;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final int TOKEN_SIZE_IN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               Clock clock,
                               @Value("${jwt.refresh-expiration}") long lifetimeMillis) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.lifetime = Duration.ofMillis(lifetimeMillis);
    }

    public record RenewedToken(String email, String newToken) {
    }

    @Transactional
    public String issue(Usuario usuario) {
        String rawToken = generateRawToken();
        Instant now = Instant.now(clock);

        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(lifetime));
        refreshTokenRepository.save(token);

        return rawToken;
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public RenewedToken renew(String rawToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalidToken);

        Usuario usuario = current.getUsuario();
        UUID usuarioId = usuario.getId();
        String email = usuario.getEmail();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllByUsuario(usuarioId, now);
            throw invalidToken();
        }

        if (!current.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        if (refreshTokenRepository.revokeIfActive(current.getId(), now) == 0) {
            refreshTokenRepository.revokeAllByUsuario(usuarioId, now);
            throw invalidToken();
        }

        return new RenewedToken(email, issue(usuario));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), Instant.now(clock)));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_SIZE_IN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessRuleException invalidToken() {
        return new BusinessRuleException(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO);
    }
}
