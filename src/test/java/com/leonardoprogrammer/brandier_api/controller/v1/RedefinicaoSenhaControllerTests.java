package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.ratelimit.RateLimitExceededException;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.service.RedefinicaoSenhaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RedefinicaoSenhaControllerTests {

    private static final String EMAIL = "pessoa@exemplo.com";

    @Mock
    private RedefinicaoSenhaService redefinicaoSenhaService;

    @Mock
    private AuthRateLimiter rateLimiter;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        AuthCookies cookies = new AuthCookies(true, 900_000, 604_800_000);
        RedefinicaoSenhaController controller = new RedefinicaoSenhaController(redefinicaoSenhaService, rateLimiter, cookies);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String corpoRedefinicao(String token, String senha) {
        return "{\"token\":\"" + token + "\",\"senha\":\"" + senha + "\"}";
    }

    @Test
    void esqueciSenhaRetornaAceitoComMensagemGenericaEConsultaOLimitador() throws Exception {
        mockMvc.perform(post("/v1/usuario/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Pe.ssoa+x@Gmail.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(containsString("Se o e-mail estiver cadastrado")));

        verify(rateLimiter).checkRecovery("password-reset-request", "127.0.0.1", "pessoa@gmail.com");
        verify(redefinicaoSenhaService).solicitar("Pe.ssoa+x@Gmail.com");
    }

    @Test
    void esqueciSenhaComEmailInvalidoRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post("/v1/usuario/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"xxx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        verify(redefinicaoSenhaService, never()).solicitar(anyString());
    }

    @Test
    void esqueciSenhaBloqueadoPeloLimiteRetorna429SemChamarOServico() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofMinutes(20)))
                .when(rateLimiter).checkRecovery(anyString(), anyString(), anyString());

        mockMvc.perform(post("/v1/usuario/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));

        verify(redefinicaoSenhaService, never()).solicitar(anyString());
    }

    @Test
    void redefinirSenhaComTokenValidoRetornaOkELimpaOsCookiesDeSessao() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRedefinicao("token-valido", "NovaSenha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Senha redefinida")))
                .andReturn();

        verify(redefinicaoSenhaService).redefinir("token-valido", "NovaSenha123");
        verify(rateLimiter).checkToken("password-reset-confirm", "127.0.0.1");
        List<String> cookies = resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }

    @Test
    void redefinirSenhaComTokenInvalidoRetornaErroPadronizadoSemLimparCookies() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.TOKEN_REDEFINICAO_INVALIDO))
                .when(redefinicaoSenhaService).redefinir("token-ruim", "NovaSenha123");

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRedefinicao("token-ruim", "NovaSenha123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_REDEFINICAO_INVALIDO"))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void redefinirSenhaComSenhaFracaRetornaErrosPorCampoENaoChamaOServico() throws Exception {
        mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRedefinicao("token", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRedefinicao("token", "somenteletras")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        verify(redefinicaoSenhaService, never()).redefinir(anyString(), anyString());
    }

    @Test
    void redefinirSenhaComSenhaAcentuadaAcimaDe72BytesEhRejeitada() throws Exception {
        String corpo = corpoRedefinicao("token", "é1".repeat(30));

        mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(corpo.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        verify(redefinicaoSenhaService, never()).redefinir(anyString(), anyString());
    }

    @Test
    void redefinirSenhaSemTokenRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post("/v1/usuario/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"NovaSenha123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='token')]").exists());
    }

    @Test
    void asRotasDeRedefinicaoAceitamSomentePost() throws Exception {
        mockMvc.perform(get("/v1/usuario/esqueci-senha")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/v1/usuario/redefinir-senha")).andExpect(status().isMethodNotAllowed());
    }
}
