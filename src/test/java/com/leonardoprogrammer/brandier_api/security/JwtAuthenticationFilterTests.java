package com.leonardoprogrammer.brandier_api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes-de-tamanho";
    private static final String OUTRO_SEGREDO = "outro-segredo-de-teste-com-mais-de-trinta-e-dois-bytes-ok";
    private static final String EMAIL = "pessoa@exemplo.com";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    private JwtService jwtService;
    private JwtAuthenticationFilter filtro;
    private UserDetails usuario;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(authenticationManager, SEGREDO, 60_000);
        AuthCookies cookiesAutenticacao = new AuthCookies(false, 60_000, 604_800_000);
        filtro = new JwtAuthenticationFilter(userDetailsService, jwtService, cookiesAutenticacao);
        usuario = User.withUsername(EMAIL).password("hash").authorities(List.of()).build();
        lenient().when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(usuario);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenDeAcessoValidoNoCookieAutenticaOUsuario() throws Exception {
        MockFilterChain cadeia = executarComCookie(jwtService.generateToken(usuario));

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacao).isNotNull();
        assertThat(((UserDetails) autenticacao.getPrincipal()).getUsername()).isEqualTo(EMAIL);
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void semCookieNaoAutenticaEContinuaACadeia() throws Exception {
        MockFilterChain cadeia = executarComCookie(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void tokenNoCabecalhoAuthorizationEhIgnorado() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/v1/qualquer");
        requisicao.addHeader("Authorization", "Bearer " + jwtService.generateToken(usuario));

        filtro.doFilter(requisicao, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenMalformadoNaoAutenticaEContinuaACadeia() throws Exception {
        MockFilterChain cadeia = executarComCookie("lixo");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadeia.getRequest()).isNotNull();
    }

    @Test
    void tokenAssinadoComOutraChaveNaoAutentica() throws Exception {
        String tokenFalso = new JwtService(authenticationManager, OUTRO_SEGREDO, 60_000).generateToken(usuario);

        executarComCookie(tokenFalso);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenExpiradoNaoAutentica() throws Exception {
        String expirado = new JwtService(authenticationManager, SEGREDO, -1_000).generateToken(usuario);

        executarComCookie(expirado);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenDeTipoAtualizacaoNaoAutentica() throws Exception {
        String tokenAtualizacao = Jwts.builder()
                .setSubject(EMAIL)
                .claim("tipo", "ATUALIZACAO")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        executarComCookie(tokenAtualizacao);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenDeUsuarioInexistenteNaoAutentica() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenThrow(new UsernameNotFoundException("x"));

        executarComCookie(jwtService.generateToken(usuario));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockFilterChain executarComCookie(String valorDoCookieDeAcesso) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/v1/qualquer");
        if (valorDoCookieDeAcesso != null) {
            requisicao.setCookies(new Cookie(AuthCookies.ACCESS_COOKIE, valorDoCookieDeAcesso));
        }
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);
        return cadeia;
    }
}
