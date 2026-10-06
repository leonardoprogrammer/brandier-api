package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.EmailVerificationTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.PasswordResetTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.RefreshTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(name = "brandier.cleanup.enabled", havingValue = "true")
public class ExpiredDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ExpiredDataCleanupService.class);
    private static final Duration INVITATION_RETENTION = Duration.ofDays(30);

    public record CleanupResult(int pendingAccounts, int refreshTokens, int verificationTokens,
                                int passwordResetTokens, int invitations, int rateLimitWindows) {
    }

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final AuthRateLimiter rateLimiter;
    private final Clock clock;
    private final Duration pendingAccountMaxAge;

    public ExpiredDataCleanupService(UsuarioRepository usuarioRepository,
                                     RefreshTokenRepository refreshTokenRepository,
                                     EmailVerificationTokenRepository emailVerificationTokenRepository,
                                     PasswordResetTokenRepository passwordResetTokenRepository,
                                     ConvitePromotorRepository conviteRepository,
                                     AuthRateLimiter rateLimiter,
                                     Clock clock,
                                     @Value("${brandier.cleanup.pending-account-max-age-days}") long pendingAccountMaxAgeDays) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.conviteRepository = conviteRepository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.pendingAccountMaxAge = Duration.ofDays(pendingAccountMaxAgeDays);
    }

    @Scheduled(cron = "${brandier.cleanup.cron}")
    @Transactional
    public CleanupResult cleanup() {
        Instant now = Instant.now(clock);

        int pendingAccounts = usuarioRepository.deleteByStatusCreatedBefore(StatusUsuario.PENDENTE, now.minus(pendingAccountMaxAge));
        int refreshTokens = refreshTokenRepository.deleteExpired(now);
        int verificationTokens = emailVerificationTokenRepository.deleteExpired(now);
        int passwordResetTokens = passwordResetTokenRepository.deleteExpired(now);
        int invitations = conviteRepository.deleteExpired(now.minus(INVITATION_RETENTION));
        int rateLimitWindows = rateLimiter.purgeExpired();

        log.info("Limpeza concluída: {} conta(s) pendente(s), {} refresh token(s), {} token(s) de verificação, {} token(s) de redefinição, {} convite(s) antigo(s) e {} janela(s) do limitador removidos.",
                pendingAccounts, refreshTokens, verificationTokens, passwordResetTokens, invitations, rateLimitWindows);

        return new CleanupResult(pendingAccounts, refreshTokens, verificationTokens, passwordResetTokens, invitations, rateLimitWindows);
    }
}
