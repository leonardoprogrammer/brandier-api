package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RelatorioDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.FiltroRelatorio;
import com.leonardoprogrammer.brandier_api.repository.RelatorioReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTests {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ReposicaoRepository reposicaoRepository;

    @Mock
    private RelatorioReposicaoRepository relatorioRepository;

    @Mock
    private ReposicaoService reposicaoService;

    @Mock
    private MarcaAcessoService acessoService;

    private RelatorioService service;
    private Usuario gestor;
    private Usuario promotor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        service = new RelatorioService(reposicaoRepository, relatorioRepository, reposicaoService, acessoService,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        gestor = usuario("Gestor");
        promotor = usuario("Promotora");
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
        lenient().when(acessoService.exigir(gestor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, true));
        lenient().when(acessoService.exigir(promotor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, false));
    }

    private static Usuario usuario(String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        return usuario;
    }

    private Reposicao reposicao(Usuario autor, Instant repostoEm, String observacoes, Object... nomesEQuantidades) {
        Loja loja = new Loja();
        loja.setId(UUID.randomUUID());
        loja.setNome("Loja Centro");
        loja.setCidade("Campinas");
        loja.setUf("SP");
        Reposicao reposicao = new Reposicao();
        reposicao.setId(UUID.randomUUID());
        reposicao.setMarca(marca);
        reposicao.setLoja(loja);
        reposicao.setAutor(autor);
        reposicao.setRepostoEm(repostoEm);
        reposicao.setObservacoes(observacoes);
        reposicao.setItens(new ArrayList<>());
        for (int i = 0; i < nomesEQuantidades.length; i += 2) {
            Produto produto = new Produto();
            produto.setId(UUID.randomUUID());
            produto.setNome((String) nomesEQuantidades[i]);
            ItemReposicao item = new ItemReposicao();
            item.setReposicao(reposicao);
            item.setProduto(produto);
            item.setQuantidade((Integer) nomesEQuantidades[i + 1]);
            reposicao.getItens().add(item);
        }
        return reposicao;
    }

    private void stubPagina(List<Reposicao> conteudo, long total) {
        when(reposicaoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<>(conteudo, invocacao.getArgument(1), total));
    }

    private static void assertErro(Throwable erro, ErrorCode esperado) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(esperado));
    }

    @Test
    void consultarComoGestorRepassaTodosOsFiltrosETraduzAsPaginas() {
        UUID lojaId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        Instant de = Instant.parse("2026-02-01T00:00:00Z");
        Instant ate = Instant.parse("2026-03-01T00:00:00Z");
        Reposicao existente = reposicao(promotor, AGORA, null, "Sabonete", 5);
        stubPagina(List.of(existente), 41);
        ReposicaoDTO dto = org.mockito.Mockito.mock(ReposicaoDTO.class);
        when(reposicaoService.paraDTO(existente, gestor, AGORA)).thenReturn(dto);
        FiltroRelatorio esperado = new FiltroRelatorio(marca.getId(), lojaId, autorId, produtoId, de, ate);
        when(relatorioRepository.totais(esperado)).thenReturn(new TotaisDTO(41L, 300L));

        RelatorioDTO relatorio = service.consultar(gestor, marca.getId(), lojaId, autorId, produtoId, de, ate, 1, 20, "data", "desc");

        assertThat(relatorio.conteudo()).containsExactly(dto);
        assertThat(relatorio.pagina()).isEqualTo(1);
        assertThat(relatorio.tamanho()).isEqualTo(20);
        assertThat(relatorio.totalElementos()).isEqualTo(41);
        assertThat(relatorio.totalPaginas()).isEqualTo(3);
        assertThat(relatorio.totais()).isEqualTo(new TotaisDTO(41L, 300L));
    }

    @Test
    void promotorSoEnxergaAsProprias_mesmoInformandoOutroAutor() {
        UUID outroAutor = UUID.randomUUID();
        stubPagina(List.of(), 0);
        FiltroRelatorio esperado = new FiltroRelatorio(marca.getId(), null, promotor.getId(), null, null, null);
        when(relatorioRepository.totais(esperado)).thenReturn(new TotaisDTO(0L, 0L));

        service.consultar(promotor, marca.getId(), null, outroAutor, null, null, null, 0, 20, "data", "desc");

        verify(relatorioRepository).totais(esperado);
        verify(relatorioRepository, never()).totais(new FiltroRelatorio(marca.getId(), null, outroAutor, null, null, null));
    }

    @Test
    void gestorSemFiltroDeAutorVeTodosOsAutores() {
        stubPagina(List.of(), 0);
        FiltroRelatorio esperado = new FiltroRelatorio(marca.getId(), null, null, null, null, null);
        when(relatorioRepository.totais(esperado)).thenReturn(new TotaisDTO(0L, 0L));

        service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, "data", "desc");

        verify(relatorioRepository).totais(esperado);
    }

    @Test
    void ordenacaoPorDataLojaEAutorMapeiaParaOsCamposEDesempataPelaCriacao() {
        stubPagina(List.of(), 0);
        when(relatorioRepository.totais(any())).thenReturn(new TotaisDTO(0L, 0L));

        service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, "data", "desc");
        service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, "loja", "asc");
        service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, "autor", "ASC");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reposicaoRepository, org.mockito.Mockito.times(3)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("repostoEm"), Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        assertThat(pageable.getAllValues().get(1).getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("loja.nome"), Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        assertThat(pageable.getAllValues().get(2).getSort().getOrderFor("autor.nome").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void ordenarPorCampoDesconhecidoLancaRequisicaoInvalidaENaoConsulta() {
        assertThatThrownBy(() -> service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, "senha; drop table", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.REQUISICAO_INVALIDA));

        verify(reposicaoRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void ordenarEmBrancoUsaAData() {
        stubPagina(List.of(), 0);
        when(relatorioRepository.totais(any())).thenReturn(new TotaisDTO(0L, 0L));

        service.consultar(gestor, marca.getId(), null, null, null, null, null, 0, 20, " ", "desc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reposicaoRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("repostoEm")).isNotNull();
    }

    @Test
    void consultarLimitaOTamanhoDaPaginaEIgnoraValoresNegativos() {
        stubPagina(List.of(), 0);
        when(relatorioRepository.totais(any())).thenReturn(new TotaisDTO(0L, 0L));

        service.consultar(gestor, marca.getId(), null, null, null, null, null, -4, 9999, "data", "desc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reposicaoRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void periodoComInicioNaoAnteriorAoFimELancaPeriodoInvalido() {
        Instant instante = Instant.parse("2026-02-01T00:00:00Z");

        assertThatThrownBy(() -> service.consultar(gestor, marca.getId(), null, null, null, instante, instante, 0, 20, "data", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.PERIODO_INVALIDO));
        assertThatThrownBy(() -> service.exportar(gestor, marca.getId(), null, null, null, instante, instante.minusSeconds(1), "data", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.PERIODO_INVALIDO));
    }

    @Test
    void periodoAbertoEmUmDosLadosEAceito() {
        stubPagina(List.of(), 0);
        when(relatorioRepository.totais(any())).thenReturn(new TotaisDTO(0L, 0L));

        assertThat(service.consultar(gestor, marca.getId(), null, null, null, Instant.parse("2026-02-01T00:00:00Z"), null, 0, 20, "data", "desc")).isNotNull();
        assertThat(service.consultar(gestor, marca.getId(), null, null, null, null, Instant.parse("2026-02-01T00:00:00Z"), 0, 20, "data", "desc")).isNotNull();
    }

    @Test
    void semAcessoALancaNaoEncontradoENaoConsulta() {
        Usuario estranho = usuario("Estranho");
        when(acessoService.exigir(estranho, marca.getId())).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        assertThatThrownBy(() -> service.consultar(estranho, marca.getId(), null, null, null, null, null, 0, 20, "data", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
        assertThatThrownBy(() -> service.exportar(estranho, marca.getId(), null, null, null, null, null, "data", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void exportarGeraCsvComBomCabecalhoEUmaLinhaPorItemNoFusoDeSaoPaulo() {
        Reposicao primeira = reposicao(promotor, Instant.parse("2026-03-10T15:30:00Z"), "Gôndola cheia",
                "Shampoo", 3, "Sabonete", 10);
        stubPagina(List.of(primeira), 1);

        RelatorioService.ArquivoCsv arquivo = service.exportar(gestor, marca.getId(), null, null, null, null, null, "data", "desc");

        String conteudo = new String(arquivo.conteudo(), StandardCharsets.UTF_8);
        assertThat(arquivo.nome()).isEqualTo("reposicoes-2026-03-10.csv");
        assertThat(conteudo).startsWith("﻿Data e hora;Marca;Loja;Cidade;UF;Autor;Produto;Quantidade;Observações\r\n");
        assertThat(conteudo.split("\r\n")).containsExactly(
                "﻿Data e hora;Marca;Loja;Cidade;UF;Autor;Produto;Quantidade;Observações",
                "10/03/2026 12:30;Marca Alfa;Loja Centro;Campinas;SP;Promotora;Sabonete;10;Gôndola cheia",
                "10/03/2026 12:30;Marca Alfa;Loja Centro;Campinas;SP;Promotora;Shampoo;3;Gôndola cheia");
    }

    @Test
    void exportarNeutralizaFormulasEEscapaCamposComSeparador() {
        Reposicao perigosa = reposicao(usuario("=cmd|' /C calc'!A0"), AGORA, "linha 1; linha 2", "Produto \"X\"", 1);
        stubPagina(List.of(perigosa), 1);

        String conteudo = new String(service.exportar(gestor, marca.getId(), null, null, null, null, null, "data", "desc").conteudo(), StandardCharsets.UTF_8);

        assertThat(conteudo).contains("'=cmd|' /C calc'!A0");
        assertThat(conteudo).contains("\"Produto \"\"X\"\"\"");
        assertThat(conteudo).contains("\"linha 1; linha 2\"");
    }

    @Test
    void exportarSemResultadosDevolveSoOCabecalho() {
        stubPagina(List.of(), 0);

        String conteudo = new String(service.exportar(gestor, marca.getId(), null, null, null, null, null, "data", "desc").conteudo(), StandardCharsets.UTF_8);

        assertThat(conteudo.split("\r\n")).hasSize(1);
    }

    @Test
    void exportarRecusaRelatoriosMaioresQueOLimite() {
        stubPagina(List.of(), RelatorioService.MAXIMO_DE_REPOSICOES_NO_CSV + 1);

        assertThatThrownBy(() -> service.exportar(gestor, marca.getId(), null, null, null, null, null, "data", "desc"))
                .satisfies(erro -> assertErro(erro, ErrorCode.RELATORIO_MUITO_GRANDE));
    }

    @Test
    void exportarAceitaExatamenteOLimite() {
        stubPagina(List.of(), RelatorioService.MAXIMO_DE_REPOSICOES_NO_CSV);

        assertThat(service.exportar(gestor, marca.getId(), null, null, null, null, null, "data", "desc").conteudo()).isNotEmpty();
    }

    @Test
    void exportarComoPromotorFiltraPelasPropriasReposicoes() {
        stubPagina(List.of(), 0);
        ArgumentCaptor<Specification> especificacao = ArgumentCaptor.forClass(Specification.class);

        service.exportar(promotor, marca.getId(), null, UUID.randomUUID(), null, null, null, "data", "desc");

        verify(reposicaoRepository).findAll(especificacao.capture(), any(Pageable.class));
        assertThat(especificacao.getValue()).isNotNull();
    }
}
