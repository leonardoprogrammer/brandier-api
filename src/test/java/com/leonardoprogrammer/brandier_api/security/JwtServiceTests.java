package com.leonardoprogrammer.brandier_api.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTests {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes-de-tamanho";
    private static final String OUTRO_SEGREDO = "outro-segredo-de-teste-com-mais-de-trinta-e-dois-bytes-ok";
    private static final long VALIDADE_EM_MILISSEGUNDOS = 60_000;

    @Mock
    private AuthenticationManager authenticationManager;

    private JwtService jwtService;
    private UserDetails usuario;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(authenticationManager, SEGREDO, VALIDADE_EM_MILISSEGUNDOS);
        usuario = User.withUsername("pessoa@exemplo.com").password("hash").authorities(List.of()).build();
    }

    @Test
    void tokenGeradoEhValidoParaOMesmoUsuario() {
        String token = jwtService.generateToken(usuario);

        assertThat(jwtService.extractUsername(token)).isEqualTo("pessoa@exemplo.com");
        assertThat(jwtService.validateToken(token, usuario)).isTrue();
    }

    @Test
    void tokenGeradoNaoEhValidoParaOutroUsuario() {
        String token = jwtService.generateToken(usuario);
        UserDetails outro = User.withUsername("outra@exemplo.com").password("hash").authorities(List.of()).build();

        assertThat(jwtService.validateToken(token, outro)).isFalse();
    }

    @Test
    void tokenExpiradoLancaExcecao() {
        JwtService servicoComTokenExpirado = new JwtService(authenticationManager, SEGREDO, -1_000);
        String token = servicoComTokenExpirado.generateToken(usuario);

        assertThatThrownBy(() -> jwtService.validateToken(token, usuario)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenAssinadoComOutraChaveLancaExcecao() {
        String tokenFalso = new JwtService(authenticationManager, OUTRO_SEGREDO, VALIDADE_EM_MILISSEGUNDOS)
                .generateToken(usuario);

        assertThatThrownBy(() -> jwtService.validateToken(tokenFalso, usuario)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenMalformadoLancaExcecao() {
        assertThatThrownBy(() -> jwtService.extractUsername("lixo")).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSemTipoNaoEhAceitoComoTokenDeAcesso() {
        String semTipo = tokenAssinado(null);

        assertThat(jwtService.validateToken(semTipo, usuario)).isFalse();
    }

    @Test
    void tokenDeTipoAtualizacaoNaoEhAceitoComoTokenDeAcesso() {
        String tipoAtualizacao = tokenAssinado("ATUALIZACAO");

        assertThat(jwtService.validateToken(tipoAtualizacao, usuario)).isFalse();
    }

    @Test
    void extrairRolesSemClaimDevolveListaVazia() {
        String token = jwtService.generateToken(usuario);

        assertThat(jwtService.extractRoles(token)).isEmpty();
    }

    @Test
    void autenticarComCredenciaisValidasDevolveTokenDeAcesso() {
        UsernamePasswordAuthenticationToken autenticado =
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(autenticado);

        String token = jwtService.authenticate("pessoa@exemplo.com", "senha");

        assertThat(jwtService.validateToken(token, usuario)).isTrue();
    }

    @Test
    void autenticarComCredenciaisInvalidasLancaBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> jwtService.authenticate("pessoa@exemplo.com", "errada"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    private String tokenAssinado(String tipo) {
        JwtBuilder construtor = Jwts.builder()
                .setSubject("pessoa@exemplo.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + VALIDADE_EM_MILISSEGUNDOS));
        if (tipo != null) {
            construtor.claim("tipo", tipo);
        }
        return construtor
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
