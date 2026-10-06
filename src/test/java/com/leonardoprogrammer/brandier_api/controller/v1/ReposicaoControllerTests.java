package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.dto.ItemReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService.ItemEntrada;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
class ReposicaoControllerTests {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ReposicaoService reposicaoService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario usuario;
    private UsernamePasswordAuthenticationToken autenticacao;
    private UUID marcaId;
    private UUID lojaId;
    private UUID produtoId;
    private String base;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReposicaoController(reposicaoService, currentUserResolver),
                        new MinhasReposicoesController(reposicaoService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("pessoa@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(usuario);
        marcaId = UUID.randomUUID();
        lojaId = UUID.randomUUID();
        produtoId = UUID.randomUUID();
        base = "/v1/marcas/" + marcaId + "/reposicoes";
    }

    private ReposicaoDTO dto(UUID id) {
        return new ReposicaoDTO(id, marcaId, "Marca Alfa", lojaId, "Loja Centro", "Promotora", AGORA, "Obs",
                List.of(new ItemReposicaoDTO(produtoId, "Sabonete", 10)), 10, AGORA, true, AGORA.plusSeconds(86400));
    }

    private String corpo(String itens) {
        return "{\"lojaId\":\"" + lojaId + "\",\"repostoEm\":\"2026-03-10T11:00:00Z\",\"itens\":" + itens
                + ",\"observacoes\":\"Obs\"}";
    }

    private String itensValidos() {
        return "[{\"produtoId\":\"" + produtoId + "\",\"quantidade\":10}]";
    }

    @Test
    void listarUsaPaginacaoPadraoEDevolveAPagina() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.listarDaMarca(usuario, marcaId, 0, 20))
                .thenReturn(new PaginaDTO<>(List.of(dto(id)), 0, 20, 1, 1));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(id.toString()))
                .andExpect(jsonPath("$.conteudo[0].marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.conteudo[0].lojaNome").value("Loja Centro"))
                .andExpect(jsonPath("$.conteudo[0].autorNome").value("Promotora"))
                .andExpect(jsonPath("$.conteudo[0].itens[0].produtoNome").value("Sabonete"))
                .andExpect(jsonPath("$.conteudo[0].itens[0].quantidade").value(10))
                .andExpect(jsonPath("$.conteudo[0].totalUnidades").value(10))
                .andExpect(jsonPath("$.conteudo[0].podeEditar").value(true))
                .andExpect(jsonPath("$.conteudo[0].repostoEm").value("2026-03-10T12:00:00Z"));
    }

    @Test
    void listarRepassaPaginaETamanhoInformados() throws Exception {
        when(reposicaoService.listarDaMarca(usuario, marcaId, 3, 50))
                .thenReturn(new PaginaDTO<>(List.of(), 3, 50, 0, 0));

        mockMvc.perform(get(base).param("pagina", "3").param("tamanho", "50").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(3));
    }

    @Test
    void listarMarcaSemAcessoRetorna404() throws Exception {
        when(reposicaoService.listarDaMarca(any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void minhasReposicoesListaAsDoUsuarioEmTodasAsMarcas() throws Exception {
        when(reposicaoService.listarMinhas(usuario, 0, 20)).thenReturn(new PaginaDTO<>(List.of(dto(UUID.randomUUID())), 0, 20, 1, 1));

        mockMvc.perform(get("/v1/reposicoes").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1));
    }

    @Test
    void buscarDevolveAReposicao() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.buscar(usuario, marcaId, id)).thenReturn(dto(id));

        mockMvc.perform(get(base + "/" + id).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void buscarReposicaoDeOutroRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.buscar(usuario, marcaId, id)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarRetorna201ComLocationERepassaOsDadosAoServico() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.criar(usuario, marcaId, lojaId, Instant.parse("2026-03-10T11:00:00Z"),
                List.of(new ItemEntrada(produtoId, 10)), "Obs")).thenReturn(dto(id));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itensValidos())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(base + "/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void criarSemDataNemObservacoesEhPermitido() throws Exception {
        when(reposicaoService.criar(usuario, marcaId, lojaId, null, List.of(new ItemEntrada(produtoId, 10)), null))
                .thenReturn(dto(UUID.randomUUID()));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lojaId\":\"" + lojaId + "\",\"itens\":" + itensValidos() + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void criarSemLojaOuSemItensRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.erros[?(@.campo=='lojaId')]").exists())
                .andExpect(jsonPath("$.erros[?(@.campo=='itens')]").exists());

        verify(reposicaoService, never()).criar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void criarComQuantidadeZeroOuAusenteRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("[{\"produtoId\":\"" + produtoId + "\",\"quantidade\":0}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_INVALIDA"));

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("[{\"produtoId\":\"" + produtoId + "\"}]")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("[{\"quantidade\":3}]")))
                .andExpect(status().isBadRequest());

        verify(reposicaoService, never()).criar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void criarComObservacoesLongasOuItemNuloRetornaErroDeValidacao() throws Exception {
        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lojaId\":\"" + lojaId + "\",\"itens\":" + itensValidos() + ",\"observacoes\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='observacoes')]").exists());

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("[null]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criarComMaisDe50ItensRetornaErroDeValidacao() throws Exception {
        String item = "{\"produtoId\":\"" + produtoId + "\",\"quantidade\":1}";
        String itens = "[" + String.join(",", java.util.Collections.nCopies(51, item)) + "]";

        mockMvc.perform(post(base).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itens)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[?(@.campo=='itens')]").exists());
    }

    @Test
    void criarComRegraDeNegocioDevolveOCodigoEOStatusEstaveis() throws Exception {
        ErrorCode[] codigos = {ErrorCode.PRODUTO_REPETIDO, ErrorCode.PRODUTO_INVALIDO, ErrorCode.DATA_REPOSICAO_FUTURA,
                ErrorCode.REPOSICAO_SEM_ITENS, ErrorCode.QUANTIDADE_INVALIDA, ErrorCode.RECURSO_NAO_ENCONTRADO};
        for (ErrorCode codigo : codigos) {
            doThrow(new BusinessRuleException(codigo)).when(reposicaoService)
                    .criar(any(), any(), any(), any(), any(), any());

            mockMvc.perform(post(base).principal(autenticacao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(itensValidos())))
                    .andExpect(status().is(codigo.getStatus().value()))
                    .andExpect(jsonPath("$.codigo").value(codigo.name()));
        }
    }

    @Test
    void atualizarRetornaAReposicaoAtualizada() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.atualizar(usuario, marcaId, id, lojaId, Instant.parse("2026-03-10T11:00:00Z"),
                List.of(new ItemEntrada(produtoId, 10)), "Obs")).thenReturn(dto(id));

        mockMvc.perform(put(base + "/" + id).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itensValidos())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void atualizarForaDaJanelaRetorna403ComCodigoEstavel() throws Exception {
        UUID id = UUID.randomUUID();
        when(reposicaoService.atualizar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.JANELA_EDICAO_ENCERRADA));

        mockMvc.perform(put(base + "/" + id).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itensValidos())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("JANELA_EDICAO_ENCERRADA"));
    }

    @Test
    void atualizarReposicaoDeOutroRetorna404() throws Exception {
        when(reposicaoService.atualizar(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(put(base + "/" + UUID.randomUUID()).principal(autenticacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itensValidos())))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirRetorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(reposicaoService).excluir(usuario, marcaId, id);
    }

    @Test
    void excluirForaDaJanelaRetorna403() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.JANELA_EDICAO_ENCERRADA)).when(reposicaoService).excluir(usuario, marcaId, id);

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("JANELA_EDICAO_ENCERRADA"));
    }

    @Test
    void metodosNaoSuportadosRetornam405() throws Exception {
        mockMvc.perform(patch(base + "/" + UUID.randomUUID()).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(base).principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/v1/reposicoes").principal(autenticacao))
                .andExpect(status().isMethodNotAllowed());
    }
}
