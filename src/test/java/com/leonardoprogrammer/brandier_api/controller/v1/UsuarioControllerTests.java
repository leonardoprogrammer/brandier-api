package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.security.JwtService;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.ratelimit.RateLimitExceededException;
import com.leonardoprogrammer.brandier_api.service.CadastroService;
import com.leonardoprogrammer.brandier_api.service.RefreshTokenService;
import com.leonardoprogrammer.brandier_api.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Mock
    private CadastroService cadastroService;

    @Mock
    private AuthRateLimiter rateLimiter;

    private MockMvc mockMvc;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        AuthCookies cookies = new AuthCookies(true, 900_000, 604_800_000);
        UsuarioController controller = new UsuarioController(jwtService, usuarioService, refreshTokenService, cookies, cadastroService, rateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Pessoa Teste");
        usuario.setEmail(EMAIL);
        usuario.setStatus(StatusUsuario.ATIVO);
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

    @Test
    void loginNormalizaOEmailAntesDeAutenticar() throws Exception {
        when(jwtService.authenticate(EMAIL, "senha-correta")).thenReturn("jwt-de-acesso");
        when(usuarioService.getUsuarioByEmail(EMAIL)).thenReturn(usuario);
        when(refreshTokenService.issue(usuario)).thenReturn("token-de-atualizacao");

        mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  Pessoa@Exemplo.COM \",\"senha\":\"senha-correta\"}"))
                .andExpect(status().isOk());

        verify(jwtService).authenticate(EMAIL, "senha-correta");
    }

    @Test
    void loginDeUsuarioPendenteRetornaEmailNaoVerificadoSemCookies() throws Exception {
        usuario.setStatus(StatusUsuario.PENDENTE);
        when(jwtService.authenticate(EMAIL, "senha-correta")).thenReturn("jwt-de-acesso");
        when(usuarioService.getUsuarioByEmail(EMAIL)).thenReturn(usuario);

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"senha-correta\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NAO_VERIFICADO"))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void cadastroValidoRetornaAceitoComMensagemGenerica() throws Exception {
        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "Senha1234", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        verify(cadastroService).cadastrar("Pessoa Teste", EMAIL, "Senha1234");
    }

    @Test
    void cadastroComEmailJaCadastradoNaCorridaRetornaAMesmaRespostaGenerica() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicado"))
                .when(cadastroService).cadastrar(anyString(), anyString(), anyString());

        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "Senha1234", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void loginBloqueadoPeloLimiteRetorna429ComTempoDeEsperaENaoAutentica() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofMinutes(7)))
                .when(rateLimiter).checkLogin(anyString(), anyString());

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "420"))
                .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"))
                .andExpect(jsonPath("$.tentarNovamenteEmSegundos").value(420))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 7 minutos."))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(jwtService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void loginConsultaOLimitadorComOIpEOEmailCanonico() throws Exception {
        when(jwtService.authenticate(EMAIL, "senha-correta")).thenReturn("jwt-de-acesso");
        when(usuarioService.getUsuarioByEmail(EMAIL)).thenReturn(usuario);
        when(refreshTokenService.issue(usuario)).thenReturn("token-de-atualizacao");

        mockMvc.perform(post("/v1/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Pe.ssoa+x@Gmail.com\",\"senha\":\"senha-correta\"}"))
                .andReturn();

        verify(rateLimiter).checkLogin("127.0.0.1", "pessoa@gmail.com");
    }

    @Test
    void cadastroBloqueadoPeloLimiteRetorna429SemChamarOServico() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofSeconds(30)))
                .when(rateLimiter).checkSignup(anyString());

        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "Senha1234", true)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 30 segundos."));

        verify(cadastroService, never()).cadastrar(anyString(), anyString(), anyString());
    }

    @Test
    void reenviarVerificacaoBloqueadoPeloLimiteRetorna429() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofMinutes(1)))
                .when(rateLimiter).checkRecovery(anyString(), anyString(), anyString());

        mockMvc.perform(post("/v1/usuario/reenviar-verificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isTooManyRequests());

        verify(cadastroService, never()).reenviarVerificacao(anyString());
    }

    @Test
    void verificarEmailBloqueadoPeloLimiteRetorna429() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofSeconds(1)))
                .when(rateLimiter).checkToken(anyString(), anyString());

        mockMvc.perform(post("/v1/usuario/verificar-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 1 segundo."));

        verify(cadastroService, never()).verificarEmail(anyString());
    }

    @Test
    void cadastroComEmailDescartavelRetornaErroPadronizado() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.EMAIL_DESCARTAVEL))
                .when(cadastroService).cadastrar(anyString(), anyString(), anyString());

        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", "pessoa@mailinator.com", "Senha1234", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("EMAIL_DESCARTAVEL"));
    }

    @Test
    void cadastroInvalidoRetornaErrosPorCampoENaoChamaOServico() throws Exception {
        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("", "nao-eh-email", "curta", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='email')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='aceitouTermos')]").exists());

        verify(cadastroService, never()).cadastrar(anyString(), anyString(), anyString());
    }

    @Test
    void cadastroComSenhaSemNumeroOuSemLetraEhRejeitado() throws Exception {
        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "somenteletras", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "123456789", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        verify(cadastroService, never()).cadastrar(anyString(), anyString(), anyString());
    }

    @Test
    void cadastroComSenhaMaiorQueOLimiteDoBcryptEhRejeitado() throws Exception {
        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("Pessoa Teste", EMAIL, "a1".repeat(37), true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());
    }

    @Test
    void cadastroComSenhaAcentuadaAcimaDe72BytesEhRejeitadoSemErroInterno() throws Exception {
        String corpo = corpoCadastro("Pessoa Teste", EMAIL, "é1".repeat(30), true);

        mockMvc.perform(post("/v1/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(corpo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        verify(cadastroService, never()).cadastrar(anyString(), anyString(), anyString());
    }

    @Test
    void cadastroNaRaizDoUsuarioAceitaSomentePost() throws Exception {
        mockMvc.perform(get("/v1/usuario")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/v1/usuario")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/v1/usuario")).andExpect(status().isMethodNotAllowed());

        verify(cadastroService, never()).cadastrar(anyString(), anyString(), anyString());
    }

    @Test
    void verificarEmailComTokenValidoRetornaOk() throws Exception {
        mockMvc.perform(post("/v1/usuario/verificar-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-valido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(cadastroService).verificarEmail("token-valido");
    }

    @Test
    void verificarEmailComTokenInvalidoRetornaErroPadronizado() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.TOKEN_VERIFICACAO_INVALIDO))
                .when(cadastroService).verificarEmail("token-ruim");

        mockMvc.perform(post("/v1/usuario/verificar-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-ruim\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACAO_INVALIDO"));
    }

    @Test
    void verificarEmailSemTokenRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post("/v1/usuario/verificar-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verify(cadastroService, never()).verificarEmail(anyString());
    }

    @Test
    void reenviarVerificacaoRetornaAceitoComMensagemGenerica() throws Exception {
        mockMvc.perform(post("/v1/usuario/reenviar-verificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());

        verify(cadastroService).reenviarVerificacao(EMAIL);
    }

    @Test
    void reenviarVerificacaoComEmailInvalidoRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post("/v1/usuario/reenviar-verificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"xxx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verify(cadastroService, never()).reenviarVerificacao(anyString());
    }

    private String corpoCadastro(String nome, String email, String senha, boolean aceitouTermos) {
        return "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha
                + "\",\"aceitouTermos\":" + aceitouTermos + "}";
    }
}
