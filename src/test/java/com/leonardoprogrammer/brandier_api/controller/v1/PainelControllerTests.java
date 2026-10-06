package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.MarcaNoPainelDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelAutorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelGestorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PeriodoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.dto.UsoDeLimitesDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PainelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PainelControllerTests {

    private static final Instant DE = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2026-03-01T00:00:00Z");

    @Mock
    private PainelService painelService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario usuario;
    private UsernamePasswordAuthenticationToken autenticacao;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PainelController(painelService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(usuario);
    }

    @Test
    void painelDoGestorRepassaOPeriodoEDevolveAsMarcasComUso() throws Exception {
        UUID marcaId = UUID.randomUUID();
        when(painelService.painelDoGestor(usuario, DE, ATE)).thenReturn(new PainelGestorDTO(
                new PeriodoDTO(DE, ATE),
                List.of(new MarcaNoPainelDTO(marcaId, "Alfa", new TotaisDTO(3L, 42L), new UsoDeLimitesDTO(2, 5, 4, 10, 1, 5))),
                2));

        mockMvc.perform(get("/v1/painel/gestor").param("de", "2026-02-01T00:00:00Z").param("ate", "2026-03-01T00:00:00Z").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteMarcas").value(2))
                .andExpect(jsonPath("$.periodo.de").value("2026-02-01T00:00:00Z"))
                .andExpect(jsonPath("$.marcas[0].marcaNome").value("Alfa"))
                .andExpect(jsonPath("$.marcas[0].totais.reposicoes").value(3))
                .andExpect(jsonPath("$.marcas[0].totais.unidades").value(42))
                .andExpect(jsonPath("$.marcas[0].uso.lojas").value(2))
                .andExpect(jsonPath("$.marcas[0].uso.limitePromotores").value(5));
    }

    @Test
    void painelDoGestorSemPeriodoRepassaNulos() throws Exception {
        when(painelService.painelDoGestor(usuario, null, null))
                .thenReturn(new PainelGestorDTO(new PeriodoDTO(DE, ATE), List.of(), 2));

        mockMvc.perform(get("/v1/painel/gestor").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcas.length()").value(0));
    }

    @Test
    void periodoInvalidoDevolve400ComCodigoEstavel() throws Exception {
        when(painelService.painelDoGestor(any(), any(), any())).thenThrow(new BusinessRuleException(ErrorCode.PERIODO_INVALIDO));

        mockMvc.perform(get("/v1/painel/gestor").param("de", "2026-02-01T00:00:00Z").param("ate", "2026-01-01T00:00:00Z").principal(autenticacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void dataEmFormatoInvalidoDevolve400() throws Exception {
        mockMvc.perform(get("/v1/painel/gestor").param("de", "ontem").principal(autenticacao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void painelDaMarcaDevolveAgregacoesEPromotoresSemAtividade() throws Exception {
        UUID marcaId = UUID.randomUUID();
        UUID lojaId = UUID.randomUUID();
        when(painelService.painelDaMarca(usuario, marcaId, DE, ATE)).thenReturn(new PainelMarcaDTO(
                new PeriodoDTO(DE, ATE), marcaId, "Alfa", new TotaisDTO(2L, 30L),
                List.of(new AgregadoDTO(lojaId, "Centro", 2L, 30L)),
                List.of(new AgregadoDTO(UUID.randomUUID(), "Ana", 2L, 30L)),
                List.of(new AgregadoDTO(UUID.randomUUID(), "Sabonete", 2L, 30L)),
                List.of(),
                new UsoDeLimitesDTO(1, 5, 3, 10, 2, 5)));

        mockMvc.perform(get("/v1/marcas/" + marcaId + "/painel").param("de", "2026-02-01T00:00:00Z").param("ate", "2026-03-01T00:00:00Z").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcaNome").value("Alfa"))
                .andExpect(jsonPath("$.totais.unidades").value(30))
                .andExpect(jsonPath("$.porLoja[0].nome").value("Centro"))
                .andExpect(jsonPath("$.porLoja[0].id").value(lojaId.toString()))
                .andExpect(jsonPath("$.porPromotor[0].nome").value("Ana"))
                .andExpect(jsonPath("$.porProduto[0].reposicoes").value(2))
                .andExpect(jsonPath("$.promotoresSemAtividade.length()").value(0))
                .andExpect(jsonPath("$.uso.produtos").value(3));
    }

    @Test
    void painelDeMarcaDeOutroGestorDevolve404() throws Exception {
        UUID marcaId = UUID.randomUUID();
        when(painelService.painelDaMarca(any(), any(), any(), any())).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get("/v1/marcas/" + marcaId + "/painel").principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void painelDasMinhasReposicoesDevolveTotaisAgregacoesEUltimas() throws Exception {
        when(painelService.painelDasMinhasReposicoes(usuario, DE, ATE)).thenReturn(new PainelAutorDTO(
                new PeriodoDTO(DE, ATE), new TotaisDTO(4L, 50L),
                List.of(new AgregadoDTO(UUID.randomUUID(), "Alfa", 4L, 50L)),
                List.of(new AgregadoDTO(UUID.randomUUID(), "Centro", 4L, 50L)),
                List.of(new AgregadoDTO(UUID.randomUUID(), "Sabonete", 4L, 50L)),
                List.of()));

        mockMvc.perform(get("/v1/painel/minhas-reposicoes").param("de", "2026-02-01T00:00:00Z").param("ate", "2026-03-01T00:00:00Z").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.reposicoes").value(4))
                .andExpect(jsonPath("$.porMarca[0].nome").value("Alfa"))
                .andExpect(jsonPath("$.porLoja[0].nome").value("Centro"))
                .andExpect(jsonPath("$.porProduto[0].nome").value("Sabonete"))
                .andExpect(jsonPath("$.ultimas.length()").value(0));
    }
}
