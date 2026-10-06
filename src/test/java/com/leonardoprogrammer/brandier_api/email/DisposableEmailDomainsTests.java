package com.leonardoprogrammer.brandier_api.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DisposableEmailDomainsTests {

    private final DisposableEmailDomains domains = new DisposableEmailDomains(true);

    @Test
    void bloqueiaDominiosDescartaveisConhecidos() {
        assertThat(domains.isDisposable("pessoa@mailinator.com")).isTrue();
        assertThat(domains.isDisposable("pessoa@guerrillamail.com")).isTrue();
    }

    @Test
    void ignoraMaiusculasEEspacos() {
        assertThat(domains.isDisposable(" Pessoa@MAILINATOR.com ")).isTrue();
    }

    @Test
    void bloqueiaSubdominiosDeUmDominioDescartavel() {
        assertThat(domains.isDisposable("pessoa@sub.mailinator.com")).isTrue();
        assertThat(domains.isDisposable("pessoa@a.b.mailinator.com")).isTrue();
    }

    @Test
    void naoBloqueiaProvedoresLegitimos() {
        assertThat(domains.isDisposable("pessoa@gmail.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@outlook.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@hotmail.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@yahoo.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@icloud.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@empresa.com.br")).isFalse();
    }

    @Test
    void naoBloqueiaDominioApenasPorTerminarIgual() {
        assertThat(domains.isDisposable("pessoa@meumailinator.com")).isFalse();
        assertThat(domains.isDisposable("pessoa@mailinator.com.br")).isFalse();
    }

    @Test
    void entradasInvalidasNaoSaoBloqueadas() {
        assertThat(domains.isDisposable(null)).isFalse();
        assertThat(domains.isDisposable("")).isFalse();
        assertThat(domains.isDisposable("pessoa@com")).isFalse();
        assertThat(domains.isDisposable("pessoa@")).isFalse();
    }

    @Test
    void desabilitadoNuncaBloqueiaECarregaNadaDoArquivo() {
        DisposableEmailDomains desabilitado = new DisposableEmailDomains(false);

        assertThat(desabilitado.isDisposable("pessoa@mailinator.com")).isFalse();
    }

    @Test
    void leOArquivoIgnorandoLinhasVaziasEEspacosEUsandoMinusculas() {
        DisposableEmailDomains amostra = new DisposableEmailDomains(true, "disposable-sample.txt");

        assertThat(amostra.isDisposable("x@alpha.example")).isTrue();
        assertThat(amostra.isDisposable("x@beta.example")).isTrue();
        assertThat(amostra.isDisposable("x@z.sub.gamma.example")).isTrue();
        assertThat(amostra.isDisposable("x@gamma.example")).isFalse();
        assertThat(amostra.isDisposable("x@mailinator.com")).isFalse();
    }

    @Test
    void arquivoAusenteNaoDerrubaAAplicacaoNemBloqueiaCadastros(CapturedOutput saida) {
        DisposableEmailDomains semArquivo = new DisposableEmailDomains(true, "nao-existe.txt");

        assertThat(semArquivo.isDisposable("x@mailinator.com")).isFalse();
        assertThat(semArquivo.isDisposable("x@gmail.com")).isFalse();
        assertThat(saida.getOut()).contains("não encontrada").contains("DESATIVADO");
    }

    @Test
    void arquivoVazioNaoDerrubaAAplicacaoNemBloqueiaCadastros(CapturedOutput saida) {
        DisposableEmailDomains vazio = new DisposableEmailDomains(true, "disposable-empty.txt");

        assertThat(vazio.isDisposable("x@mailinator.com")).isFalse();
        assertThat(saida.getOut()).contains("vazia").contains("DESATIVADO");
    }

    @Test
    void desabilitadoPorConfiguracaoNaoAvisaSobreArquivoAusente(CapturedOutput saida) {
        new DisposableEmailDomains(false, "nao-existe.txt");

        assertThat(saida.getOut()).doesNotContain("DESATIVADO");
    }

    @Test
    void arquivoCarregadoRegistraAQuantidadeDeDominios(CapturedOutput saida) {
        new DisposableEmailDomains(true, "disposable-sample.txt");

        assertThat(saida.getOut()).contains("carregada: 3 domínios");
    }
}
