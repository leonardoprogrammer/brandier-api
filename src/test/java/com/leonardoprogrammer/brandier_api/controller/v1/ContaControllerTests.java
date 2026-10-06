package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.ratelimit.AuthRateLimiter;
import com.leonardoprogrammer.brandier_api.ratelimit.RateLimitExceededException;
import com.leonardoprogrammer.brandier_api.security.AuthCookies;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ContaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContaControllerTests {

    @Mock
    private ContaService contaService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private AuthRateLimiter rateLimiter;

    private MockMvc mockMvc;
    private Usuario usuario;
    private UsernamePasswordAuthenticationToken autenticacao;

    @BeforeEach
    void preparar() {
        AuthCookies cookies = new AuthCookies(true, 900_000, 604_800_000);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContaController(contaService, currentUserResolver, rateLimiter, cookies))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(usuario);
    }

    @Test
    void excluirComASenhaCertaRetorna204ELimpaOsCookiesDeSessao() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/v1/usuario/excluir-conta").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"Senha1234\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(rateLimiter).checkAccountDeletion(usuario.getId().toString());
        verify(contaService).excluir(usuario, "Senha1234");
        List<String> cookies = resultado.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(c -> assertThat(c).contains("brandier_access=").contains("Max-Age=0"));
        assertThat(cookies).anySatisfy(c -> assertThat(c).contains("brandier_refresh=").contains("Max-Age=0"));
    }

    @Test
    void excluirComASenhaErradaRetorna403ComCodigoEstavelENaoLimpaCookies() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.SENHA_INCORRETA)).when(contaService).excluir(any(), anyString());

        MvcResult resultado = mockMvc.perform(post("/v1/usuario/excluir-conta").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"errada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SENHA_INCORRETA"))
                .andReturn();

        assertThat(resultado.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void excluirSemSenhaRetornaErroDeValidacaoENaoChamaOServico() throws Exception {
        mockMvc.perform(post("/v1/usuario/excluir-conta").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='senha')]").exists());

        verify(contaService, never()).excluir(any(), anyString());
    }

    @Test
    void excluirBloqueadoPeloLimiteRetorna429SemChamarOServico() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofSeconds(60))).when(rateLimiter).checkAccountDeletion(anyString());

        mockMvc.perform(post("/v1/usuario/excluir-conta").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"Senha1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        verify(contaService, never()).excluir(any(), anyString());
    }

    @Test
    void soAceitaPost() throws Exception {
        mockMvc.perform(get("/v1/usuario/excluir-conta").principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
    }
}
