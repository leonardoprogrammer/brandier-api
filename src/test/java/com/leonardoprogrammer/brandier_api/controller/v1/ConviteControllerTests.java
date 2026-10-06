package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ConviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConviteControllerTests {

    private static final Instant AGORA = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private ConviteService conviteService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario gestor;
    private UsernamePasswordAuthenticationToken autenticacao;
    private UUID marcaId;
    private String base;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ConviteController(conviteService, currentUserResolver, Clock.fixed(AGORA, ZoneOffset.UTC)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("gestor@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(gestor);
        marcaId = UUID.randomUUID();
        base = "/v1/marcas/" + marcaId + "/convites";
    }

    private ConvitePromotor convite(String email) {
        ConvitePromotor convite = new ConvitePromotor();
        convite.setId(UUID.randomUUID());
        convite.setEmail(email);
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setCriadoEm(AGORA);
        convite.setExpiraEm(AGORA.plusSeconds(7 * 24 * 3600));
        return convite;
    }

    @Test
    void convidarRetorna201ComOConviteSemExporOToken() throws Exception {
        ConvitePromotor criado = convite("nova@exemplo.com");
        when(conviteService.convidar(gestor, marcaId, "nova@exemplo.com")).thenReturn(criado);

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nova@exemplo.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nova@exemplo.com"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.expiraEm").value("2026-03-08T12:00:00Z"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void convidarComEmailInvalidoOuVazioRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"isso-nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='email')]").exists());

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(conviteService, never()).convidar(any(), any(), anyString());
    }

    @Test
    void convidarComRegraDeNegocioRetornaOCodigoEstavel() throws Exception {
        ErrorCode[] codigos = {ErrorCode.LIMITE_PROMOTORES_ATINGIDO, ErrorCode.CONVITE_JA_PENDENTE,
                ErrorCode.PROMOTOR_JA_VINCULADO, ErrorCode.CONVITE_PROPRIO_EMAIL};
        for (ErrorCode codigo : codigos) {
            doThrow(new BusinessRuleException(codigo)).when(conviteService).convidar(any(), any(), anyString());

            mockMvc.perform(post(base).principal(autenticacao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nova@exemplo.com\"}"))
                    .andExpect(status().is(codigo.getStatus().value()))
                    .andExpect(jsonPath("$.codigo").value(codigo.name()));
        }
    }

    @Test
    void convidarEmMarcaDeOutroGestorRetorna404() throws Exception {
        when(conviteService.convidar(any(), any(), anyString()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nova@exemplo.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelarRetorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(conviteService).cancelar(gestor, marcaId, id);
    }

    @Test
    void cancelarConviteInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO)).when(conviteService).cancelar(gestor, marcaId, id);

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoHaListagemNemBuscaDeConvitePorId() throws Exception {
        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
    }
}
