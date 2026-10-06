package com.leonardoprogrammer.brandier_api.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MaxBytesValidatorTests {

    private MaxBytesValidator validator;

    @BeforeEach
    void preparar() {
        MaxBytes restricao = Mockito.mock(MaxBytes.class);
        Mockito.when(restricao.value()).thenReturn(72);
        validator = new MaxBytesValidator();
        validator.initialize(restricao);
    }

    @Test
    void nuloEhValido() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void textoAsciiNoLimiteEhValido() {
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
    }

    @Test
    void textoAsciiAcimaDoLimiteEhInvalido() {
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
    }

    @Test
    void caracteresAcentuadosContamDoisBytes() {
        assertThat(validator.isValid("é".repeat(36), null)).isTrue();
        assertThat(validator.isValid("é".repeat(37), null)).isFalse();
    }

    @Test
    void emojiContaQuatroBytes() {
        assertThat(validator.isValid("😀".repeat(18), null)).isTrue();
        assertThat(validator.isValid("😀".repeat(19), null)).isFalse();
    }
}
