package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.repository.EmailVerificationTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.PasswordResetTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.RefreshTokenRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExpiredDataCleanupServiceTests {

    private static final Instant AGORA = Instant.parse("2026-01-10T03:30:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private AuthRateLimiter rateLimiter;

    private ExpiredDataCleanupService service;

    @BeforeEach
    void preparar() {
        service = new ExpiredDataCleanupService(usuarioRepository, refreshTokenRepository,
                emailVerificationTokenRepository, passwordResetTokenRepository, rateLimiter,
                Clock.fixed(AGORA, ZoneOffset.UTC), 7);
    }

    @Test
    void removeContasPendentesComMaisDeSeteDiasETokensEJanelasExpirados(CapturedOutput saida) {
        when(usuarioRepository.deleteByStatusCreatedBefore(StatusUsuario.PENDENTE, Instant.parse("2026-01-03T03:30:00Z"))).thenReturn(3);
        when(refreshTokenRepository.deleteExpired(AGORA)).thenReturn(5);
        when(emailVerificationTokenRepository.deleteExpired(AGORA)).thenReturn(2);
        when(passwordResetTokenRepository.deleteExpired(AGORA)).thenReturn(1);
        when(rateLimiter.purgeExpired()).thenReturn(40);

        ExpiredDataCleanupService.CleanupResult resultado = service.cleanup();

        assertThat(resultado).isEqualTo(new ExpiredDataCleanupService.CleanupResult(3, 5, 2, 1, 40));
        assertThat(saida.getOut()).contains("3 conta(s) pendente(s)").contains("5 refresh token(s)");
    }

    @Test
    void usaOLimiteDeIdadeConfiguradoParaContasPendentes() {
        ExpiredDataCleanupService umDia = new ExpiredDataCleanupService(usuarioRepository, refreshTokenRepository,
                emailVerificationTokenRepository, passwordResetTokenRepository, rateLimiter,
                Clock.fixed(AGORA, ZoneOffset.UTC), 1);

        umDia.cleanup();

        verify(usuarioRepository).deleteByStatusCreatedBefore(StatusUsuario.PENDENTE, Instant.parse("2026-01-09T03:30:00Z"));
    }

    @Test
    void naoApagaContasAtivasSoConsultaPorPendentes() {
        service.cleanup();

        verify(usuarioRepository).deleteByStatusCreatedBefore(eq(StatusUsuario.PENDENTE), any());
    }

    @Test
    void estaAgendadoComACronConfiguravel() throws NoSuchMethodException {
        Method metodo = ExpiredDataCleanupService.class.getMethod("cleanup");

        Scheduled agendamento = metodo.getAnnotation(Scheduled.class);

        assertThat(agendamento).isNotNull();
        assertThat(agendamento.cron()).isEqualTo("${brandier.cleanup.cron}");
    }
}
