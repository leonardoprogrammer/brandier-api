package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.LojaService;
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
class LojaControllerTests {

    private static final String CORPO_VALIDO = "{\"nome\":\"Loja Centro\",\"cidade\":\"Campinas\",\"uf\":\"SP\"}";

    @Mock
    private LojaService lojaService;

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
        mockMvc = MockMvcBuilders.standaloneSetup(new LojaController(lojaService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("gestor@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(gestor);
        marcaId = UUID.randomUUID();
        base = "/v1/marcas/" + marcaId + "/lojas";
    }

    private Loja loja(String nome, String endereco) {
        Loja loja = new Loja();
        loja.setId(UUID.randomUUID());
        loja.setNome(nome);
        loja.setCidade("Campinas");
        loja.setUf("SP");
        loja.setEndereco(endereco);
        loja.setCriadoEm(Instant.parse("2026-01-01T12:00:00Z"));
        return loja;
    }

    @Test
    void listarDevolveAsLojasEOLimite() throws Exception {
        when(lojaService.listar(gestor, marcaId)).thenReturn(List.of(loja("Centro", "Rua A, 1"), loja("Norte", null)));
        when(lojaService.limiteDeLojas()).thenReturn(5);

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(5))
                .andExpect(jsonPath("$.lojas.length()").value(2))
                .andExpect(jsonPath("$.lojas[0].nome").value("Centro"))
                .andExpect(jsonPath("$.lojas[0].cidade").value("Campinas"))
                .andExpect(jsonPath("$.lojas[0].uf").value("SP"))
                .andExpect(jsonPath("$.lojas[0].endereco").value("Rua A, 1"))
                .andExpect(jsonPath("$.lojas[0].criadoEm").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.lojas[1].endereco").doesNotExist());
    }

    @Test
    void listarMarcaDeOutroGestorRetorna404() throws Exception {
        when(lojaService.listar(gestor, marcaId)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void buscarDevolveALoja() throws Exception {
        Loja existente = loja("Centro", null);
        when(lojaService.buscar(gestor, marcaId, existente.getId())).thenReturn(existente);

        mockMvc.perform(get(base + "/" + existente.getId()).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Centro"));
    }

    @Test
    void buscarLojaInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(lojaService.buscar(gestor, marcaId, id)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarRetorna201ComLocationEALoja() throws Exception {
        Loja criada = loja("Loja Centro", null);
        when(lojaService.criar(gestor, marcaId, "Loja Centro", "Campinas", "SP", null)).thenReturn(criada);

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(base + "/" + criada.getId())))
                .andExpect(jsonPath("$.nome").value("Loja Centro"));
    }

    @Test
    void criarNoLimiteRetorna409ComCodigoDeLimite() throws Exception {
        when(lojaService.criar(any(), eq(marcaId), anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.LIMITE_LOJAS_ATINGIDO));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_LOJAS_ATINGIDO"));
    }

    @Test
    void criarComNomeDuplicadoRetorna409() throws Exception {
        when(lojaService.criar(any(), eq(marcaId), anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.LOJA_NOME_DUPLICADO));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LOJA_NOME_DUPLICADO"));
    }

    @Test
    void criarSemCamposObrigatoriosRetornaErrosPorCampo() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  \",\"cidade\":\"\",\"uf\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='cidade')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='uf')]").exists());

        verify(lojaService, never()).criar(any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void criarComUfInvalidaOuTextosLongosEhRejeitado() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "n".repeat(81) + "\",\"cidade\":\"" + "c".repeat(81)
                                + "\",\"uf\":\"XX\",\"endereco\":\"" + "e".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='cidade')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='uf')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='endereco')]").exists());
    }

    @Test
    void criarAceitaUfEmMinusculas() throws Exception {
        when(lojaService.criar(any(), eq(marcaId), anyString(), anyString(), eq("sp"), any()))
                .thenReturn(loja("Loja Centro", null));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Loja Centro\",\"cidade\":\"Campinas\",\"uf\":\"sp\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void atualizarRetornaALojaAtualizada() throws Exception {
        Loja atualizada = loja("Nova", "Rua B, 2");
        when(lojaService.atualizar(gestor, marcaId, atualizada.getId(), "Nova", "Campinas", "SP", "Rua B, 2"))
                .thenReturn(atualizada);

        mockMvc.perform(put(base + "/" + atualizada.getId()).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nova\",\"cidade\":\"Campinas\",\"uf\":\"SP\",\"endereco\":\"Rua B, 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nova"))
                .andExpect(jsonPath("$.endereco").value("Rua B, 2"));
    }

    @Test
    void atualizarLojaDeOutraMarcaRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(lojaService.atualizar(any(), eq(marcaId), eq(id), anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(put(base + "/" + id).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirRetorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(lojaService).excluir(gestor, marcaId, id);
    }

    @Test
    void excluirLojaDeOutraMarcaRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO))
                .when(lojaService).excluir(gestor, marcaId, id);

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void metodosNaoSuportadosRetornam405() throws Exception {
        mockMvc.perform(patch(base + "/" + UUID.randomUUID()).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(base).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
    }
}
