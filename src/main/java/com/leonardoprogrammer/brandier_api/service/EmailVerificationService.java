package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.EmailVerificationToken;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.EmailVerificationTokenRepository;
import com.leonardoprogrammer.brandier_api.security.OpaqueTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final Clock clock;
    private final Duration lifetime;
    private final Duration resendCooldown;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    Clock clock,
                                    @Value("${brandier.verification.token-expiration-hours}") long lifetimeHours,
                                    @Value("${brandier.verification.resend-cooldown-seconds}") long resendCooldownSeconds) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
        this.lifetime = Duration.ofHours(lifetimeHours);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
    }

    @Transactional
    public Optional<String> issue(Usuario usuario) {
        Instant now = Instant.now(clock);

        boolean insideCooldown = tokenRepository.findFirstByUsuarioIdOrderByCreatedAtDesc(usuario.getId())
                .map(latest -> latest.getCreatedAt().plus(resendCooldown).isAfter(now))
                .orElse(false);
        if (insideCooldown) {
            return Optional.empty();
        }

        tokenRepository.deleteAllByUsuario(usuario.getId());

        String rawToken = OpaqueTokens.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUsuario(usuario);
        token.setTokenHash(OpaqueTokens.hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(lifetime));
        tokenRepository.save(token);

        return Optional.of(rawToken);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public Usuario consume(String rawToken) {
        Instant now = Instant.now(clock);
        EmailVerificationToken token = tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .orElseThrow(EmailVerificationService::invalidToken);

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        if (tokenRepository.markUsedIfUnused(token.getId(), now) == 0) {
            throw invalidToken();
        }

        return token.getUsuario();
    }

    private static BusinessRuleException invalidToken() {
        return new BusinessRuleException(ErrorCode.TOKEN_VERIFICACAO_INVALIDO);
    }
}
