package com.leonardoprogrammer.brandier_api.ratelimit;

import com.leonardoprogrammer.brandier_api.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTests {

    private static final Duration JANELA = Duration.ofMinutes(15);

    private MutableClock relogio;
    private FixedWindowRateLimiter limitador;

    @BeforeEach
    void preparar() {
        relogio = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        limitador = new FixedWindowRateLimiter(relogio);
    }

    @Test
    void permiteAteOLimiteDentroDaJanela() {
        for (int i = 0; i < 3; i++) {
            assertThat(limitador.tryConsume("chave", 3, JANELA)).isEmpty();
        }
    }

    @Test
    void bloqueiaAPartirDaTentativaSeguinteAoLimiteEInformaOTempoDeEspera() {
        for (int i = 0; i < 3; i++) {
            limitador.tryConsume("chave", 3, JANELA);
        }
        relogio.advance(Duration.ofMinutes(5));

        Optional<Duration> espera = limitador.tryConsume("chave", 3, JANELA);

        assertThat(espera).contains(Duration.ofMinutes(10));
    }

    @Test
    void tentativasBloqueadasNaoEstendemAJanela() {
        for (int i = 0; i < 4; i++) {
            limitador.tryConsume("chave", 3, JANELA);
        }
        relogio.advance(Duration.ofMinutes(14));
        assertThat(limitador.tryConsume("chave", 3, JANELA)).isPresent();

        relogio.advance(Duration.ofMinutes(1));

        assertThat(limitador.tryConsume("chave", 3, JANELA)).isEmpty();
    }

    @Test
    void novaJanelaComecaDepoisQueAAnteriorExpira() {
        for (int i = 0; i < 3; i++) {
            limitador.tryConsume("chave", 3, JANELA);
        }
        assertThat(limitador.tryConsume("chave", 3, JANELA)).isPresent();

        relogio.advance(JANELA);

        for (int i = 0; i < 3; i++) {
            assertThat(limitador.tryConsume("chave", 3, JANELA)).isEmpty();
        }
        assertThat(limitador.tryConsume("chave", 3, JANELA)).isPresent();
    }

    @Test
    void chavesDiferentesTemContadoresIndependentes() {
        for (int i = 0; i < 3; i++) {
            limitador.tryConsume("a", 3, JANELA);
        }

        assertThat(limitador.tryConsume("a", 3, JANELA)).isPresent();
        assertThat(limitador.tryConsume("b", 3, JANELA)).isEmpty();
    }

    @Test
    void limiteUmPermiteUmaTentativaPorJanela() {
        assertThat(limitador.tryConsume("chave", 1, JANELA)).isEmpty();
        assertThat(limitador.tryConsume("chave", 1, JANELA)).isPresent();
    }

    @Test
    void purgeExpiredRemoveSomenteJanelasVencidas() {
        limitador.tryConsume("curta", 5, Duration.ofMinutes(1));
        limitador.tryConsume("longa", 5, Duration.ofHours(1));
        relogio.advance(Duration.ofMinutes(2));

        int removidas = limitador.purgeExpired();

        assertThat(removidas).isEqualTo(1);
        assertThat(limitador.size()).isEqualTo(1);
    }

    @Test
    void comOLimitadorCheioLiberaChavesNovasSemRegistrarEMantemOTamanho() {
        for (int i = 0; i < 100_000; i++) {
            limitador.tryConsume("chave-" + i, 5, JANELA);
        }
        assertThat(limitador.size()).isEqualTo(100_000);

        Optional<Duration> resultado = limitador.tryConsume("chave-nova", 1, JANELA);
        Optional<Duration> repetida = limitador.tryConsume("chave-nova", 1, JANELA);

        assertThat(resultado).isEmpty();
        assertThat(repetida).isEmpty();
        assertThat(limitador.size()).isEqualTo(100_000);
    }

    @Test
    void comOLimitadorCheioLimpaAsJanelasVencidasAntesDeLiberar() {
        for (int i = 0; i < 100_000; i++) {
            limitador.tryConsume("chave-" + i, 5, Duration.ofMinutes(1));
        }
        relogio.advance(Duration.ofMinutes(2));

        limitador.tryConsume("chave-nova", 1, JANELA);
        Optional<Duration> segunda = limitador.tryConsume("chave-nova", 1, JANELA);

        assertThat(segunda).isPresent();
        assertThat(limitador.size()).isEqualTo(1);
    }
}
