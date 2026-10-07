package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.dto.ConvitePendenteDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteRecebidoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConviteRecebidoControllerTests {

    private static final String CORPO = "{\"token\":\"abc\"}";

    @Mock
    private ConviteService conviteService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario usuario;
    private UsernamePasswordAuthenticationToken autenticacao;
    private Marca marca;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ConviteRecebidoController(conviteService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(usuario);

        Usuario gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        gestor.setNome("Gestor Silva");
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
    }

    @Test
    void consultarDevolveMarcaGestorEPrazoSemExporDadosInternos() throws Exception {
        when(conviteService.consultar(usuario, "abc")).thenReturn(
                new ConviteRecebidoDTO("Marca Alfa", "Gestor Silva", "pessoa@exemplo.com", Instant.parse("2026-03-08T12:00:00Z")));

        mockMvc.perform(post("/v1/convites/consultar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.gestorNome").value("Gestor Silva"))
                .andExpect(jsonPath("$.email").value("pessoa@exemplo.com"))
                .andExpect(jsonPath("$.expiraEm").value("2026-03-08T12:00:00Z"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.gestorId").doesNotExist());
    }

    @Test
    void consultarConviteInvalidoRetorna400ComCodigoEstavel() throws Exception {
        when(conviteService.consultar(any(), anyString())).thenThrow(new BusinessRuleException(ErrorCode.TOKEN_CONVITE_INVALIDO));

        mockMvc.perform(post("/v1/convites/consultar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_CONVITE_INVALIDO"));
    }

    @Test
    void aceitarDevolveOVinculoCriado() throws Exception {
        when(conviteService.aceitar(usuario, "abc")).thenReturn(new VinculoPromotorDTO(
                marca.getId(), "Marca Alfa", "Gestor Silva", true, Instant.parse("2026-03-01T12:00:00Z")));

        mockMvc.perform(post("/v1/convites/aceitar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcaId").value(marca.getId().toString()))
                .andExpect(jsonPath("$.marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.gestorNome").value("Gestor Silva"))
                .andExpect(jsonPath("$.entrouEm").value("2026-03-01T12:00:00Z"));
    }

    @Test
    void aceitarConviteInvalidoRetorna400() throws Exception {
        when(conviteService.aceitar(any(), anyString())).thenThrow(new BusinessRuleException(ErrorCode.TOKEN_CONVITE_INVALIDO));

        mockMvc.perform(post("/v1/convites/aceitar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_CONVITE_INVALIDO"));
    }

    @Test
    void recusarRetorna204() throws Exception {
        mockMvc.perform(post("/v1/convites/recusar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNoContent());

        verify(conviteService).recusar(usuario, "abc");
    }

    @Test
    void recusarConviteInvalidoRetorna400() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.TOKEN_CONVITE_INVALIDO)).when(conviteService).recusar(any(), anyString());

        mockMvc.perform(post("/v1/convites/recusar").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recebidosListaOsConvitesPendentesSemExporToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(conviteService.listarRecebidos(usuario)).thenReturn(List.of(
                new ConvitePendenteDTO(id, "Marca Alfa", "Gestor Silva", Instant.parse("2026-03-08T12:00:00Z"), Instant.parse("2026-03-01T12:00:00Z"))));

        mockMvc.perform(get("/v1/convites/recebidos").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convites.length()").value(1))
                .andExpect(jsonPath("$.convites[0].id").value(id.toString()))
                .andExpect(jsonPath("$.convites[0].marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.convites[0].gestorNome").value("Gestor Silva"))
                .andExpect(jsonPath("$.convites[0].expiraEm").value("2026-03-08T12:00:00Z"))
                .andExpect(jsonPath("$.convites[0].token").doesNotExist())
                .andExpect(jsonPath("$.convites[0].tokenHash").doesNotExist());
    }

    @Test
    void recebidosSemConvitesDevolveListaVazia() throws Exception {
        when(conviteService.listarRecebidos(usuario)).thenReturn(List.of());

        mockMvc.perform(get("/v1/convites/recebidos").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convites.length()").value(0));
    }

    @Test
    void aceitarPorIdDevolveOVinculoCriado() throws Exception {
        UUID id = UUID.randomUUID();
        when(conviteService.aceitarPorId(usuario, id)).thenReturn(new VinculoPromotorDTO(
                marca.getId(), "Marca Alfa", "Gestor Silva", true, Instant.parse("2026-03-01T12:00:00Z")));

        mockMvc.perform(post("/v1/convites/" + id + "/aceitar").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.entrouEm").value("2026-03-01T12:00:00Z"));
    }

    @Test
    void aceitarPorIdDeConviteAlheioDevolve404() throws Exception {
        UUID id = UUID.randomUUID();
        when(conviteService.aceitarPorId(usuario, id)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(post("/v1/convites/" + id + "/aceitar").principal(autenticacao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void recusarPorIdDevolve204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/v1/convites/" + id + "/recusar").principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(conviteService).recusarPorId(usuario, id);
    }

    @Test
    void recusarPorIdDeConviteAlheioDevolve404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO)).when(conviteService).recusarPorId(usuario, id);

        mockMvc.perform(post("/v1/convites/" + id + "/recusar").principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void idQueNaoEUuidDevolve400() throws Exception {
        mockMvc.perform(post("/v1/convites/nao-e-uuid/aceitar").principal(autenticacao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenAusenteOuVazioRetornaErroDeValidacaoENaoChamaOServico() throws Exception {
        for (String caminho : List.of("/v1/convites/consultar", "/v1/convites/aceitar", "/v1/convites/recusar")) {
            mockMvc.perform(post(caminho).principal(autenticacao)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"  \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros[?(@.campo=='token')]").exists());
        }

        verify(conviteService, never()).consultar(any(), anyString());
        verify(conviteService, never()).aceitar(any(), anyString());
        verify(conviteService, never()).recusar(any(), anyString());
    }
}
