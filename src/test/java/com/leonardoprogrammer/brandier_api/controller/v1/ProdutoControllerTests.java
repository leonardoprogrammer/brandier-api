package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ProdutoService;
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
class ProdutoControllerTests {

    private static final String CORPO_VALIDO = "{\"nome\":\"Sabonete\",\"codigo\":\"SKU-1\"}";

    @Mock
    private ProdutoService produtoService;

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
        mockMvc = MockMvcBuilders.standaloneSetup(new ProdutoController(produtoService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("gestor@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(gestor);
        marcaId = UUID.randomUUID();
        base = "/v1/marcas/" + marcaId + "/produtos";
    }

    private Produto produto(String nome, String codigo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setCodigo(codigo);
        produto.setCriadoEm(Instant.parse("2026-01-01T12:00:00Z"));
        return produto;
    }

    @Test
    void listarDevolveOsProdutosEOLimite() throws Exception {
        when(produtoService.listar(gestor, marcaId)).thenReturn(List.of(produto("Sabonete", "SKU-1"), produto("Shampoo", null)));
        when(produtoService.limiteDeProdutos()).thenReturn(10);

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(10))
                .andExpect(jsonPath("$.produtos.length()").value(2))
                .andExpect(jsonPath("$.produtos[0].nome").value("Sabonete"))
                .andExpect(jsonPath("$.produtos[0].codigo").value("SKU-1"))
                .andExpect(jsonPath("$.produtos[0].criadoEm").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.produtos[1].codigo").doesNotExist());
    }

    @Test
    void listarMarcaDeOutroGestorRetorna404() throws Exception {
        when(produtoService.listar(gestor, marcaId)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void buscarDevolveOProduto() throws Exception {
        Produto existente = produto("Sabonete", null);
        when(produtoService.buscar(gestor, marcaId, existente.getId())).thenReturn(existente);

        mockMvc.perform(get(base + "/" + existente.getId()).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sabonete"));
    }

    @Test
    void buscarProdutoInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.buscar(gestor, marcaId, id)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarRetorna201ComLocationEOProduto() throws Exception {
        Produto criado = produto("Sabonete", "SKU-1");
        when(produtoService.criar(gestor, marcaId, "Sabonete", "SKU-1")).thenReturn(criado);

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(base + "/" + criado.getId())))
                .andExpect(jsonPath("$.nome").value("Sabonete"));
    }

    @Test
    void criarSemCodigoEhPermitido() throws Exception {
        when(produtoService.criar(eq(gestor), eq(marcaId), eq("Sabonete"), any())).thenReturn(produto("Sabonete", null));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sabonete\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void criarNoLimiteRetorna409ComCodigoDeLimite() throws Exception {
        when(produtoService.criar(any(), eq(marcaId), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.LIMITE_PRODUTOS_ATINGIDO));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMITE_PRODUTOS_ATINGIDO"));
    }

    @Test
    void criarComNomeDuplicadoRetorna409() throws Exception {
        when(produtoService.criar(any(), eq(marcaId), anyString(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.PRODUTO_NOME_DUPLICADO));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRODUTO_NOME_DUPLICADO"));
    }

    @Test
    void criarSemNomeRetornaErroPorCampo() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists());

        verify(produtoService, never()).criar(any(), any(), anyString(), any());
    }

    @Test
    void criarComTextosLongosEhRejeitado() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "n".repeat(81) + "\",\"codigo\":\"" + "c".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='nome')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='codigo')]").exists());
    }

    @Test
    void atualizarRetornaOProdutoAtualizado() throws Exception {
        Produto atualizado = produto("Novo", "COD-2");
        when(produtoService.atualizar(gestor, marcaId, atualizado.getId(), "Novo", "COD-2")).thenReturn(atualizado);

        mockMvc.perform(put(base + "/" + atualizado.getId()).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo\",\"codigo\":\"COD-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo"))
                .andExpect(jsonPath("$.codigo").value("COD-2"));
    }

    @Test
    void atualizarProdutoDeOutraMarcaRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizar(any(), eq(marcaId), eq(id), anyString(), any()))
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

        verify(produtoService).excluir(gestor, marcaId, id);
    }

    @Test
    void excluirProdutoDeOutraMarcaRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO))
                .when(produtoService).excluir(gestor, marcaId, id);

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
