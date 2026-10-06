package com.leonardoprogrammer.brandier_api.ratelimit;

import com.leonardoprogrammer.brandier_api.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTests {

    private MutableClock relogio;
    private FixedWindowRateLimiter limitador;
    private AuthRateLimiter authRateLimiter;

    @BeforeEach
    void preparar() {
        relogio = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        limitador = new FixedWindowRateLimiter(relogio);
        authRateLimiter = new AuthRateLimiter(propriedades(true), limitador);
    }

    private static RateLimitProperties propriedades(boolean habilitado) {
        return new RateLimitProperties(habilitado, 4, 2, 15, 2, 3, 2, 3, 60);
    }

    @Test
    void loginBloqueiaPeloLimiteDoEmailMesmoComIpsDiferentes() {
        authRateLimiter.checkLogin("1.1.1.1", "pessoa@exemplo.com");
        authRateLimiter.checkLogin("2.2.2.2", "pessoa@exemplo.com");

        assertThatThrownBy(() -> authRateLimiter.checkLogin("3.3.3.3", "pessoa@exemplo.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void loginBloqueiaPeloLimiteDoIpMesmoComEmailsDiferentes() {
        for (int i = 0; i < 4; i++) {
            authRateLimiter.checkLogin("1.1.1.1", "pessoa" + i + "@exemplo.com");
        }

        assertThatThrownBy(() -> authRateLimiter.checkLogin("1.1.1.1", "outra@exemplo.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void loginDeOutroIpEEmailNaoEAfetado() {
        for (int i = 0; i < 4; i++) {
            authRateLimiter.checkLogin("1.1.1.1", "pessoa" + i + "@exemplo.com");
        }

        assertThatCode(() -> authRateLimiter.checkLogin("9.9.9.9", "livre@exemplo.com")).doesNotThrowAnyException();
    }

    @Test
    void excecaoInformaQuantoTempoEsperar() {
        authRateLimiter.checkLogin("1.1.1.1", "pessoa@exemplo.com");
        authRateLimiter.checkLogin("1.1.1.1", "pessoa@exemplo.com");
        relogio.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> authRateLimiter.checkLogin("1.1.1.1", "pessoa@exemplo.com"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        erro -> assertThat(erro.getRetryAfter()).isEqualTo(Duration.ofMinutes(10)));
    }

    @Test
    void cadastroLimitaPorIp() {
        authRateLimiter.checkSignup("1.1.1.1");
        authRateLimiter.checkSignup("1.1.1.1");

        assertThatThrownBy(() -> authRateLimiter.checkSignup("1.1.1.1")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> authRateLimiter.checkSignup("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void recuperacaoLimitaPorEmailEPorOperacaoSeparadamente() {
        for (int i = 0; i < 2; i++) {
            authRateLimiter.checkRecovery("password-reset-request", "1.1.1." + i, "pessoa@exemplo.com");
        }

        assertThatThrownBy(() -> authRateLimiter.checkRecovery("password-reset-request", "1.1.1.10", "pessoa@exemplo.com"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> authRateLimiter.checkRecovery("resend-verification", "1.1.1.10", "pessoa@exemplo.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void tokensLimitamPorIp() {
        authRateLimiter.checkToken("verify-email", "1.1.1.1");
        authRateLimiter.checkToken("verify-email", "1.1.1.1");
        authRateLimiter.checkToken("verify-email", "1.1.1.1");

        assertThatThrownBy(() -> authRateLimiter.checkToken("verify-email", "1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void exclusaoDeContaLimitaPorUsuarioENaoAfetaOutros() {
        authRateLimiter.checkAccountDeletion("usuario-1");
        authRateLimiter.checkAccountDeletion("usuario-1");

        assertThatThrownBy(() -> authRateLimiter.checkAccountDeletion("usuario-1"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> authRateLimiter.checkAccountDeletion("usuario-2")).doesNotThrowAnyException();
    }

    @Test
    void desabilitadoNuncaBloqueiaENuncaRegistra() {
        AuthRateLimiter desabilitado = new AuthRateLimiter(propriedades(false), limitador);

        for (int i = 0; i < 50; i++) {
            desabilitado.checkLogin("1.1.1.1", "pessoa@exemplo.com");
            desabilitado.checkSignup("1.1.1.1");
        }

        assertThat(limitador.size()).isZero();
    }

    @Test
    void purgeExpiredRemoveJanelasVencidas() {
        authRateLimiter.checkSignup("1.1.1.1");
        relogio.advance(Duration.ofHours(2));

        assertThat(authRateLimiter.purgeExpired()).isEqualTo(1);
    }
}
