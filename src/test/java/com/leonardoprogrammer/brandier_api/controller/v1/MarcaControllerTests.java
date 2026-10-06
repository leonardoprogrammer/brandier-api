package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.MarcaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarcaControllerTests {

    @Mock
    private MarcaService marcaService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario gestor;
    private UsernamePasswordAuthenticationToken autenticacao;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new MarcaController(marcaService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("gestor@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(gestor);
    }

    private Marca marca(String nome, String descricao) {
        Marca marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome(nome);
        marca.setDescricao(descricao);
        marca.setCriadoEm(Instant.parse("2026-01-01T12:00:00Z"));
        return marca;
    }

    @Test
    void listarDevolveAsMarcasEOLimite() throws Exception {
        when(marcaService.listar(gestor)).thenReturn(List.of(marca("Alfa", "Descrição"), marca("Beta", null)));
        when(marcaService.limiteDeMarcas()).thenReturn(2);

        mockMvc.perform(get("/v1/marcas").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(2))
                .andExpect(jsonPath("$.marcas.length()").value(2))
                .andExpect(jsonPath("$.marcas[0].nome").value("Alfa"))
                .andExpect(jsonPath("$.marcas[0].descricao").value("Descrição"))
                .andExpect(jsonPath("$.marcas[0].criadoEm").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.marcas[1].descricao").doesNotExist());
    }

    @Test
    void listarSemMarcasDevolveListaVazia() throws Exception {
        when(marcaService.listar(gestor)).thenReturn(List.of());
        when(marcaService.limiteDeMarcas()).thenReturn(2);

        mockMvc.perform(get("/v1/marcas").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcas.length()").value(0));
    }

    @Test
    void buscarDevolveAMarca() throws Exception {
        Marca alfa = marca("Alfa", null);
        when(marcaService.buscar(gestor, alfa.getId())).thenReturn(alfa);

        mockMvc.perform(get("/v1/marcas/" + alfa.getId()).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alfa.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Alfa"));
    }

    @Test
    void buscarMarcaDeOutroGestorRetorna404Padronizado() throws Exception {
        UUID id = UUID.randomUUID();
        when(marcaService.buscar(gestor, id)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get("/v1/marcas/" + id).principal(autenticacao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void buscarComIdInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/v1/marcas/nao-eh-uuid").principal(autenticacao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criarRetorna201ComLocationEAMarcaCriada() throws Exception {
        Marca criada = marca("Nova", "Descrição");
        when(marcaService.criar(gestor, "Nova", "Descrição")).thenReturn(criada);

        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nova\",\"descricao\":\"Descrição\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/v1/marcas/" + criada.getId())))
                .andExpect(jsonPath("$.nome").value("Nova"));
    }

    @Test
    void criarSemDescricaoEhPermitido() throws Exception {
        when(marcaService.criar(eq(gestor), eq("Nova"), any())).thenReturn(marca("Nova", null));

        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nova\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void criarNoLimiteRetorna409ComCodigoDeLimite() throws Exception {
        when(marcaService.criar(any(), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.LIMITE_MARCAS_ATINGIDO));

        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Terceira\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_MARCAS_ATINGIDO"));
    }

    @Test
    void criarComNomeDuplicadoRetorna409() throws Exception {
        when(marcaService.criar(any(), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.MARCA_NOME_DUPLICADO));

        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Repetida\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MARCA_NOME_DUPLICADO"));
    }

    @Test
    void criarSemNomeOuComTextoLongoRetornaErrosPorCampo() throws Exception {
        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"   \",\"descricao\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='descricao')]").exists());

        verify(marcaService, never()).criar(any(), anyString(), any());
    }

    @Test
    void criarComNomeAcimaDe80CaracteresEhRejeitado() throws Exception {
        mockMvc.perform(post("/v1/marcas").principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "n".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists());
    }

    @Test
    void atualizarRetornaAMarcaAtualizada() throws Exception {
        Marca atualizada = marca("Novo nome", "Nova");
        when(marcaService.atualizar(gestor, atualizada.getId(), "Novo nome", "Nova")).thenReturn(atualizada);

        mockMvc.perform(put("/v1/marcas/" + atualizada.getId()).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo nome\",\"descricao\":\"Nova\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo nome"));
    }

    @Test
    void atualizarMarcaDeOutroGestorRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(marcaService.atualizar(any(), eq(id), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(put("/v1/marcas/" + id).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Qualquer\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirRetorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/marcas/" + id).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(marcaService).excluir(gestor, id);
    }

    @Test
    void excluirMarcaDeOutroGestorRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO))
                .when(marcaService).excluir(gestor, id);

        mockMvc.perform(delete("/v1/marcas/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void metodosNaoSuportadosRetornam405() throws Exception {
        mockMvc.perform(patch("/v1/marcas/" + UUID.randomUUID()).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/v1/marcas").principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
    }
}
