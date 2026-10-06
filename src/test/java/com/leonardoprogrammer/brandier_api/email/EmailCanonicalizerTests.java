package com.leonardoprogrammer.brandier_api.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailCanonicalizerTests {

    @Test
    void normalizarRemoveEspacosEUsaMinusculas() {
        assertThat(EmailCanonicalizer.normalize("  Pessoa@Exemplo.COM ")).isEqualTo("pessoa@exemplo.com");
    }

    @Test
    void normalizarNuloDevolveNulo() {
        assertThat(EmailCanonicalizer.normalize(null)).isNull();
        assertThat(EmailCanonicalizer.canonicalize(null)).isNull();
    }

    @Test
    void gmailIgnoraPontosEMarcadorComMais() {
        assertThat(EmailCanonicalizer.canonicalize("p.e.s.s.o.a@gmail.com")).isEqualTo("pessoa@gmail.com");
        assertThat(EmailCanonicalizer.canonicalize("pessoa+brandier@gmail.com")).isEqualTo("pessoa@gmail.com");
        assertThat(EmailCanonicalizer.canonicalize("Pe.ssoa+x.y@GMAIL.com")).isEqualTo("pessoa@gmail.com");
    }

    @Test
    void googlemailEhTratadoComoGmail() {
        assertThat(EmailCanonicalizer.canonicalize("pessoa@googlemail.com")).isEqualTo("pessoa@gmail.com");
    }

    @Test
    void provedoresComSubenderecoIgnoramOMarcadorMasMantemOsPontos() {
        assertThat(EmailCanonicalizer.canonicalize("pe.ssoa+x@outlook.com")).isEqualTo("pe.ssoa@outlook.com");
        assertThat(EmailCanonicalizer.canonicalize("pessoa+x@hotmail.com")).isEqualTo("pessoa@hotmail.com");
        assertThat(EmailCanonicalizer.canonicalize("pessoa+x@icloud.com")).isEqualTo("pessoa@icloud.com");
        assertThat(EmailCanonicalizer.canonicalize("pessoa+x@proton.me")).isEqualTo("pessoa@proton.me");
    }

    @Test
    void outrosDominiosNaoSaoAlterados() {
        assertThat(EmailCanonicalizer.canonicalize("pe.ssoa+x@empresa.com.br")).isEqualTo("pe.ssoa+x@empresa.com.br");
        assertThat(EmailCanonicalizer.canonicalize("pessoa+x@yahoo.com")).isEqualTo("pessoa+x@yahoo.com");
    }

    @Test
    void marcadorNoInicioDoNomeNaoZeraOEndereco() {
        assertThat(EmailCanonicalizer.canonicalize("+tag@gmail.com")).isEqualTo("+tag@gmail.com");
    }

    @Test
    void enderecoSemNomeLocalOuSemDominioFicaComoEstaNormalizado() {
        assertThat(EmailCanonicalizer.canonicalize("...@gmail.com")).isEqualTo("...@gmail.com");
        assertThat(EmailCanonicalizer.canonicalize("sem-arroba")).isEqualTo("sem-arroba");
        assertThat(EmailCanonicalizer.canonicalize("pessoa@")).isEqualTo("pessoa@");
        assertThat(EmailCanonicalizer.canonicalize("@gmail.com")).isEqualTo("@gmail.com");
    }

    @Test
    void canonicalizarEhIdempotente() {
        String canonico = EmailCanonicalizer.canonicalize("Pe.ssoa+x@googlemail.com");

        assertThat(EmailCanonicalizer.canonicalize(canonico)).isEqualTo(canonico);
    }
}
