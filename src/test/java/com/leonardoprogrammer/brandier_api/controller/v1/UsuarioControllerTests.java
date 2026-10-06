package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.security.JwtService;
import com.leonardoprogrammer.brandier_api.service.RefreshTokenService;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTests {

    private static final String EMAIL = "pessoa@exemplo.com";

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private MockMvc mockMvc;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        AuthCookies cookies = new AuthCookies(true, 900_000, 604_800_000);
        UsuarioController controller = new UsuarioController(jwtService, usuarioService, refreshTokenService, cookies);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Pessoa Teste");
        usuario.setEmail(EMAIL);
    }

    @Test
    void loginDefineOsDoisCookiesHttpOnlyENaoDevolveTokensNoCorpo() throws Exception {
        when(jwtService.authenticate(EMAIL, "senha-correta")).thenReturn("jwt-de-acesso");
        when(usuarioService.getUsuarioByEmail(EMAIL)).thenReturn(usuario);
        when(refreshTokenService.issue(usuario)).thenReturn("token-de-atualizacao");

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"senha-correta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        List<String> cookies = resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("brandier_access=jwt-de-acesso").contains("HttpOnly").contains("SameSite=Lax"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("brandier_refresh=token-de-atualizacao").contains("HttpOnly").contains("SameSite=Strict"));
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("jwt-de-acesso", "token-de-atualizacao");
    }

    @Test
    void loginComCredenciaisInvalidasRetornaNaoAutenticadoSemCookies() throws Exception {
        when(jwtService.authenticate(EMAIL, "errada")).thenThrow(new BadCredentialsException("x"));

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void loginComCorpoVazioRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refreshComCookieValidoRenovaOsDoisCookies() throws Exception {
        UserDetails userDetails = User.withUsername(EMAIL).password("hash").authorities(List.of()).build();
        when(refreshTokenService.renew("atualizacao-antiga"))
                .thenReturn(new RefreshTokenService.RenewedToken(EMAIL, "atualizacao-nova"));
        when(usuarioService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-novo");

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/refresh-token")
                        .cookie(new Cookie(AuthCookies.REFRESH_COOKIE, "atualizacao-antiga")))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> cookies = resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie).startsWith("brandier_access=jwt-novo"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie).startsWith("brandier_refresh=atualizacao-nova"));
    }

    @Test
    void refreshSemCookieRetornaTokenInvalido() throws Exception {
        mockMvc.perform(post("/v1/usuario/refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_ATUALIZACAO_INVALIDO"));

        verify(refreshTokenService, never()).renew(anyString());
    }

    @Test
    void refreshComTokenInvalidoLimpaOsCookies() throws Exception {
        when(refreshTokenService.renew("reusado"))
                .thenThrow(new BusinessRuleException(ErrorCode.TOKEN_ATUALIZACAO_INVALIDO));

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/refresh-token")
                        .cookie(new Cookie(AuthCookies.REFRESH_COOKIE, "reusado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_ATUALIZACAO_INVALIDO"))
                .andReturn();

        List<String> cookies = resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }

    @Test
    void logoutRevogaOTokenDoCookieELimpaOsCookies() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/v1/usuario/logout")
                        .cookie(new Cookie(AuthCookies.REFRESH_COOKIE, "atualizacao-atual")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        verify(refreshTokenService).revoke("atualizacao-atual");
        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }

    @Test
    void logoutSemCookieTambemLimpaOsCookiesSemErro() throws Exception {
        mockMvc.perform(post("/v1/usuario/logout"))
                .andExpect(status().isOk());

        verify(refreshTokenService, never()).revoke(anyString());
    }

    @Test
    void euDevolveOUsuarioAutenticado() throws Exception {
        when(usuarioService.getUsuarioByEmail(EMAIL)).thenReturn(usuario);
        UsernamePasswordAuthenticationToken autenticacao =
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());

        mockMvc.perform(get("/v1/usuario/eu").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nome").value("Pessoa Teste"));
    }
}
