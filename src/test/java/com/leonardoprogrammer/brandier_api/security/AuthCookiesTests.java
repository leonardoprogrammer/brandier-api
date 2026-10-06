package com.leonardoprogrammer.brandier_api.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiesTests {

    private static final long ACESSO_EM_MILISSEGUNDOS = 900_000;
    private static final long ATUALIZACAO_EM_MILISSEGUNDOS = 604_800_000;

    private final MockHttpServletRequest requisicao = new MockHttpServletRequest();
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();

    @Test
    void cookieDeAcessoEhHttpOnlySameSiteLaxEComValidadeDoToken() {
        AuthCookies cookies = criar(true);
        requisicao.setContextPath("/api");

        cookies.addAccess(requisicao, resposta, "token-de-acesso");

        String cookie = cookieDaResposta(AuthCookies.ACCESS_COOKIE);
        assertThat(cookie)
                .contains("brandier_access=token-de-acesso")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/")
                .contains("Max-Age=900");
    }

    @Test
    void cookieDeAtualizacaoEhHttpOnlySameSiteStrictERestritoAoCaminhoDoUsuario() {
        AuthCookies cookies = criar(true);
        requisicao.setContextPath("/api");

        cookies.addRefresh(requisicao, resposta, "token-de-atualizacao");

        String cookie = cookieDaResposta(AuthCookies.REFRESH_COOKIE);
        assertThat(cookie)
                .contains("brandier_refresh=token-de-atualizacao")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/usuario")
                .contains("Max-Age=604800");
    }

    @Test
    void cookieNaoTemSecureQuandoConfiguradoParaDesenvolvimento() {
        AuthCookies cookies = criar(false);

        cookies.addAccess(requisicao, resposta, "token");

        assertThat(cookieDaResposta(AuthCookies.ACCESS_COOKIE)).doesNotContain("Secure").contains("HttpOnly");
    }

    @Test
    void limparExpiraOsDoisCookiesNosMesmosCaminhos() {
        AuthCookies cookies = criar(true);
        requisicao.setContextPath("/api");

        cookies.clear(requisicao, resposta);

        assertThat(cookieDaResposta(AuthCookies.ACCESS_COOKIE))
                .contains("brandier_access=;")
                .contains("Max-Age=0")
                .contains("Path=/api/");
        assertThat(cookieDaResposta(AuthCookies.REFRESH_COOKIE))
                .contains("brandier_refresh=;")
                .contains("Max-Age=0")
                .contains("Path=/api/v1/usuario");
    }

    @Test
    void leTokensDosCookiesDaRequisicao() {
        AuthCookies cookies = criar(true);
        requisicao.setCookies(
                new Cookie(AuthCookies.ACCESS_COOKIE, "acesso"),
                new Cookie(AuthCookies.REFRESH_COOKIE, "atualizacao"),
                new Cookie("outro", "x"));

        assertThat(cookies.readAccess(requisicao)).contains("acesso");
        assertThat(cookies.readRefresh(requisicao)).contains("atualizacao");
    }

    @Test
    void semCookiesOuComValorVazioNaoHaToken() {
        AuthCookies cookies = criar(true);

        assertThat(cookies.readAccess(requisicao)).isEmpty();

        requisicao.setCookies(new Cookie(AuthCookies.ACCESS_COOKIE, ""));
        assertThat(cookies.readAccess(requisicao)).isEmpty();
        assertThat(cookies.readRefresh(requisicao)).isEmpty();
    }

    private AuthCookies criar(boolean secure) {
        return new AuthCookies(secure, ACESSO_EM_MILISSEGUNDOS, ATUALIZACAO_EM_MILISSEGUNDOS);
    }

    private String cookieDaResposta(String nome) {
        List<String> cabecalhos = resposta.getHeaders(HttpHeaders.SET_COOKIE);
        return cabecalhos.stream()
                .filter(cabecalho -> cabecalho.startsWith(nome + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie não encontrado: " + nome));
    }
}
