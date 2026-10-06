package com.leonardoprogrammer.brandier_api.security;

import com.leonardoprogrammer.brandier_api.config.TurnstileProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileServiceTests {

    private static final String URL = "https://verificacao.exemplo.com/siteverify";

    private MockRestServiceServer servidor;
    private RestClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder construtor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(construtor).build();
        cliente = construtor.build();
    }

    private TurnstileService servico(boolean habilitado, String segredo) {
        return new TurnstileService(new TurnstileProperties(habilitado, segredo, URL, 5), cliente);
    }

    private static void assertCaptchaInvalido(Throwable erro) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_INVALIDO));
    }

    @Test
    void desabilitadoNaoChamaOCloudflareEAceitaQualquerToken() {
        servidor.expect(never(), requestTo(URL));

        assertThatCode(() -> servico(false, "").verify(null, "1.2.3.4")).doesNotThrowAnyException();
        assertThatCode(() -> servico(false, "").verify("qualquer", "1.2.3.4")).doesNotThrowAnyException();

        servidor.verify();
    }

    @Test
    void habilitadoSemChaveSecretaFalhaNaSubida() {
        assertThatThrownBy(() -> servico(true, "  ")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-key");
    }

    @Test
    void tokenValidoEnviaSegredoTokenEIpEmFormularioEAceita() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("secret=segredo-de-teste"),
                        containsString("response=token-do-widget"),
                        containsString("remoteip=1.2.3.4"))))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> servico(true, "segredo-de-teste").verify("token-do-widget", "1.2.3.4")).doesNotThrowAnyException();

        servidor.verify();
    }

    @Test
    void semIpNaoEnviaOCampoRemoteip() {
        servidor.expect(requestTo(URL))
                .andExpect(content().string(not(containsString("remoteip"))))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        servico(true, "segredo").verify("token", null);

        servidor.verify();
    }

    @Test
    void respostaComSuccessFalsoLancaCaptchaInvalido() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> servico(true, "segredo").verify("token-ruim", "1.2.3.4"))
                .satisfies(TurnstileServiceTests::assertCaptchaInvalido);
    }

    @Test
    void respostaSemOCampoSuccessOuVaziaLancaCaptchaInvalido() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> servico(true, "segredo").verify("token", "1.2.3.4"))
                .satisfies(TurnstileServiceTests::assertCaptchaInvalido);

        servidor.reset();
        servidor.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> servico(true, "segredo").verify("token", "1.2.3.4"))
                .satisfies(TurnstileServiceTests::assertCaptchaInvalido);
    }

    @Test
    void successComoTextoNaoContaComoSucesso() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"success\":\"true\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> servico(true, "segredo").verify("token", "1.2.3.4"))
                .satisfies(TurnstileServiceTests::assertCaptchaInvalido);
    }

    @Test
    void erroDoServidorDoCloudflareFalhaFechadoComCaptchaInvalido() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> servico(true, "segredo").verify("token", "1.2.3.4"))
                .satisfies(TurnstileServiceTests::assertCaptchaInvalido);
    }

    @Test
    void tokenAusenteOuEmBrancoLancaCaptchaInvalidoSemChamarOCloudflare() {
        servidor.expect(never(), requestTo(URL));

        assertThatThrownBy(() -> servico(true, "segredo").verify(null, "1.2.3.4")).satisfies(TurnstileServiceTests::assertCaptchaInvalido);
        assertThatThrownBy(() -> servico(true, "segredo").verify("   ", "1.2.3.4")).satisfies(TurnstileServiceTests::assertCaptchaInvalido);

        servidor.verify();
    }
}
