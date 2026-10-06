package com.leonardoprogrammer.brandier_api.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvEscaperTests {

    @Test
    void textoSimplesNaoEAlterado() {
        assertThat(CsvEscaper.campo("Loja Centro")).isEqualTo("Loja Centro");
        assertThat(CsvEscaper.campo("Gôndola de promoção")).isEqualTo("Gôndola de promoção");
    }

    @Test
    void nuloViraCampoVazio() {
        assertThat(CsvEscaper.campo(null)).isEmpty();
    }

    @Test
    void campoComSeparadorEntraEntreAspas() {
        assertThat(CsvEscaper.campo("Rua A; 10")).isEqualTo("\"Rua A; 10\"");
    }

    @Test
    void aspasSaoDobradasEOCampoFicaEntreAspas() {
        assertThat(CsvEscaper.campo("Produto \"Premium\"")).isEqualTo("\"Produto \"\"Premium\"\"\"");
    }

    @Test
    void quebrasDeLinhaMantemOCampoEmUmaCelula() {
        assertThat(CsvEscaper.campo("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(CsvEscaper.campo("linha 1\r\nlinha 2")).isEqualTo("\"linha 1\r\nlinha 2\"");
    }

    @Test
    void virgulaNaoPrecisaDeAspasPoisOSeparadorEPontoEVirgula() {
        assertThat(CsvEscaper.campo("Campinas, SP")).isEqualTo("Campinas, SP");
    }

    @Test
    void camposQueComecamComCaracteresDeFormulaRecebemApostrofo() {
        assertThat(CsvEscaper.campo("=SOMA(A1:A9)")).isEqualTo("'=SOMA(A1:A9)");
        assertThat(CsvEscaper.campo("+55 19 99999-0000")).isEqualTo("'+55 19 99999-0000");
        assertThat(CsvEscaper.campo("-10")).isEqualTo("'-10");
        assertThat(CsvEscaper.campo("@usuario")).isEqualTo("'@usuario");
        assertThat(CsvEscaper.campo("\tcomando")).isEqualTo("'\tcomando");
    }

    @Test
    void caracteresDeFormulaNoMeioDoTextoNaoSaoAlterados() {
        assertThat(CsvEscaper.campo("a=b")).isEqualTo("a=b");
        assertThat(CsvEscaper.campo("preço - promoção")).isEqualTo("preço - promoção");
    }

    @Test
    void formulaComSeparadorRecebeApostrofoEAspas() {
        assertThat(CsvEscaper.campo("=A1;B1")).isEqualTo("\"'=A1;B1\"");
    }

    @Test
    void linhaJuntaOsCamposComPontoEVirgulaETerminaComCrLf() {
        assertThat(CsvEscaper.linha(List.of("a", "b;c", "", "d"))).isEqualTo("a;\"b;c\";;d\r\n");
    }

    @Test
    void linhaTrataCamposNulos() {
        assertThat(CsvEscaper.linha(Arrays.asList("a", null))).isEqualTo("a;\r\n");
    }
}
