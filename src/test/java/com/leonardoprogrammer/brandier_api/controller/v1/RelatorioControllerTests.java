package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.dto.ItemReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RelatorioDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.RelatorioService;
import com.leonardoprogrammer.brandier_api.service.RelatorioService.ArquivoCsv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RelatorioControllerTests {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private RelatorioService relatorioService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario usuario;
    private UsernamePasswordAuthenticationToken autenticacao;
    private UUID marcaId;
    private String base;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new RelatorioController(relatorioService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(usuario);
        marcaId = UUID.randomUUID();
        base = "/v1/marcas/" + marcaId + "/relatorios/reposicoes";
    }

    private ReposicaoDTO reposicao() {
        return new ReposicaoDTO(UUID.randomUUID(), marcaId, "Alfa", UUID.randomUUID(), "Centro", "Ana", AGORA, null,
                List.of(new ItemReposicaoDTO(UUID.randomUUID(), "Sabonete", 5)), 5, AGORA, true, null);
    }

    @Test
    void consultarUsaOsPadroesERepassaAoServico() throws Exception {
        when(relatorioService.consultar(usuario, marcaId, null, null, null, null, null, 0, 20, "data", "desc"))
                .thenReturn(new RelatorioDTO(List.of(reposicao()), 0, 20, 1, 1, new TotaisDTO(1L, 5L)));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].lojaNome").value("Centro"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totais.reposicoes").value(1))
                .andExpect(jsonPath("$.totais.unidades").value(5));
    }

    @Test
    void consultarRepassaTodosOsFiltrosEAOrdenacao() throws Exception {
        UUID lojaId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        when(relatorioService.consultar(usuario, marcaId, lojaId, autorId, produtoId,
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"), 2, 50, "loja", "asc"))
                .thenReturn(new RelatorioDTO(List.of(), 2, 50, 0, 0, new TotaisDTO(0L, 0L)));

        mockMvc.perform(get(base).principal(autenticacao)
                        .param("lojaId", lojaId.toString())
                        .param("autorId", autorId.toString())
                        .param("produtoId", produtoId.toString())
                        .param("de", "2026-02-01T00:00:00Z")
                        .param("ate", "2026-03-01T00:00:00Z")
                        .param("pagina", "2").param("tamanho", "50")
                        .param("ordenar", "loja").param("direcao", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2));
    }

    @Test
    void idOuDataInvalidosDevolvem400() throws Exception {
        mockMvc.perform(get(base).param("lojaId", "nao-e-uuid").principal(autenticacao))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base).param("de", "ontem").principal(autenticacao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errosDeRegraDevolvemOCodigoEstavel() throws Exception {
        when(relatorioService.consultar(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new BusinessRuleException(ErrorCode.PERIODO_INVALIDO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void marcaSemAcessoDevolve404() throws Exception {
        when(relatorioService.consultar(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportarDevolveCsvComoAnexoSemCache() throws Exception {
        byte[] conteudo = "﻿Data;Loja\r\n10/03/2026;Centro\r\n".getBytes(StandardCharsets.UTF_8);
        when(relatorioService.exportar(usuario, marcaId, null, null, null, null, null, "data", "desc"))
                .thenReturn(new ArquivoCsv("reposicoes-2026-03-10.csv", conteudo));

        mockMvc.perform(get(base + "/csv").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reposicoes-2026-03-10.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(conteudo));
    }

    @Test
    void exportarRepassaOsFiltros() throws Exception {
        UUID lojaId = UUID.randomUUID();
        when(relatorioService.exportar(usuario, marcaId, lojaId, null, null, Instant.parse("2026-02-01T00:00:00Z"), null, "autor", "asc"))
                .thenReturn(new ArquivoCsv("reposicoes-2026-03-10.csv", new byte[0]));

        mockMvc.perform(get(base + "/csv").principal(autenticacao)
                        .param("lojaId", lojaId.toString())
                        .param("de", "2026-02-01T00:00:00Z")
                        .param("ordenar", "autor").param("direcao", "asc"))
                .andExpect(status().isOk());
    }

    @Test
    void exportarRelatorioGrandeDemaisDevolve400ComCodigoEstavel() throws Exception {
        when(relatorioService.exportar(any(), any(), any(), any(), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new BusinessRuleException(ErrorCode.RELATORIO_MUITO_GRANDE));

        mockMvc.perform(get(base + "/csv").principal(autenticacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_MUITO_GRANDE"));
    }
}
