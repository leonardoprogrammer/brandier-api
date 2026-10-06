package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelAutorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelGestorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PainelServiceTests {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant FIM_PADRAO = AGORA.plus(Duration.ofMinutes(5));

    @Mock
    private ReposicaoRepository reposicaoRepository;

    @Mock
    private ReposicaoService reposicaoService;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private PromotorRepository promotorRepository;

    @Mock
    private ConvitePromotorRepository conviteRepository;

    private PainelService service;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        service = new PainelService(reposicaoRepository, reposicaoService, marcaRepository, lojaRepository,
                produtoRepository, promotorRepository, conviteRepository, new LimitsProperties(2, 5, 10, 5),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
    }

    private void stubUso(Marca alvo, long lojas, long produtos, long promotores, long convites) {
        when(lojaRepository.countByMarcaId(alvo.getId())).thenReturn(lojas);
        when(produtoRepository.countByMarcaIdAndAtivoTrue(alvo.getId())).thenReturn(produtos);
        when(promotorRepository.countByMarcaId(alvo.getId())).thenReturn(promotores);
        when(conviteRepository.countByMarcaIdAndStatusAndExpiraEmAfter(alvo.getId(), StatusConvite.PENDENTE, AGORA)).thenReturn(convites);
    }

    private static void assertPeriodoInvalido(Throwable erro) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(ErrorCode.PERIODO_INVALIDO));
    }

    @Test
    void painelDoGestorUsaOsUltimos30DiasPorPadraoEZeraMarcasSemAtividade() {
        Marca outra = new Marca();
        outra.setId(UUID.randomUUID());
        outra.setNome("Marca Beta");
        outra.setGestor(gestor);
        Instant inicio = FIM_PADRAO.minus(Duration.ofDays(30));
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId())).thenReturn(List.of(marca, outra));
        when(reposicaoRepository.porMarcaDoGestor(gestor.getId(), inicio, FIM_PADRAO))
                .thenReturn(List.of(new AgregadoDTO(marca.getId(), "Marca Alfa", 3L, 42L)));
        stubUso(marca, 2, 4, 1, 2);
        stubUso(outra, 0, 0, 0, 0);

        PainelGestorDTO painel = service.painelDoGestor(gestor, null, null);

        assertThat(painel.periodo().de()).isEqualTo(inicio);
        assertThat(painel.periodo().ate()).isEqualTo(FIM_PADRAO);
        assertThat(painel.limiteMarcas()).isEqualTo(2);
        assertThat(painel.marcas()).hasSize(2);
        assertThat(painel.marcas().get(0).marcaNome()).isEqualTo("Marca Alfa");
        assertThat(painel.marcas().get(0).totais()).isEqualTo(new TotaisDTO(3L, 42L));
        assertThat(painel.marcas().get(0).uso().promotores()).isEqualTo(3);
        assertThat(painel.marcas().get(0).uso().limitePromotores()).isEqualTo(5);
        assertThat(painel.marcas().get(0).uso().lojas()).isEqualTo(2);
        assertThat(painel.marcas().get(0).uso().limiteLojas()).isEqualTo(5);
        assertThat(painel.marcas().get(0).uso().produtos()).isEqualTo(4);
        assertThat(painel.marcas().get(0).uso().limiteProdutos()).isEqualTo(10);
        assertThat(painel.marcas().get(1).totais()).isEqualTo(new TotaisDTO(0L, 0L));
    }

    @Test
    void painelDoGestorSemMarcasDevolveListaVazia() {
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId())).thenReturn(List.of());

        PainelGestorDTO painel = service.painelDoGestor(gestor, null, null);

        assertThat(painel.marcas()).isEmpty();
        assertThat(painel.limiteMarcas()).isEqualTo(2);
    }

    @Test
    void painelDoGestorRespeitaOPeriodoInformado() {
        Instant de = Instant.parse("2026-02-01T00:00:00Z");
        Instant ate = Instant.parse("2026-03-01T00:00:00Z");
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId())).thenReturn(List.of());

        PainelGestorDTO painel = service.painelDoGestor(gestor, de, ate);

        assertThat(painel.periodo().de()).isEqualTo(de);
        assertThat(painel.periodo().ate()).isEqualTo(ate);
        verify(reposicaoRepository).porMarcaDoGestor(gestor.getId(), de, ate);
    }

    @Test
    void periodoComInicioNaoAnteriorAoFimOuMaiorQue366DiasEhRejeitado() {
        Instant de = Instant.parse("2026-02-01T00:00:00Z");

        assertThatThrownBy(() -> service.painelDoGestor(gestor, de, de)).satisfies(PainelServiceTests::assertPeriodoInvalido);
        assertThatThrownBy(() -> service.painelDoGestor(gestor, de, de.minusSeconds(1))).satisfies(PainelServiceTests::assertPeriodoInvalido);
        assertThatThrownBy(() -> service.painelDoGestor(gestor, de, de.plus(Duration.ofDays(367)))).satisfies(PainelServiceTests::assertPeriodoInvalido);
        verify(reposicaoRepository, never()).porMarcaDoGestor(any(), any(), any());
        assertThat(service.painelDoGestor(gestor, de, de.plus(Duration.ofDays(366))).marcas()).isEmpty();
    }

    @Test
    void soOInicioInformadoUsaOFimPadrao() {
        Instant de = Instant.parse("2026-03-01T00:00:00Z");
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId())).thenReturn(List.of());

        PainelGestorDTO painel = service.painelDoGestor(gestor, de, null);

        assertThat(painel.periodo().de()).isEqualTo(de);
        assertThat(painel.periodo().ate()).isEqualTo(FIM_PADRAO);
    }

    @Test
    void painelDaMarcaReuneAgregacoesPromotoresSemAtividadeEUsoDosLimites() {
        Instant inicio = FIM_PADRAO.minus(Duration.ofDays(30));
        Usuario inativo = new Usuario();
        inativo.setId(UUID.randomUUID());
        inativo.setNome("Sem Atividade");
        inativo.setEmail("sem@exemplo.com");
        Promotor promotor = new Promotor();
        promotor.setId(UUID.randomUUID());
        promotor.setUsuario(inativo);
        promotor.setMarca(marca);
        promotor.setEntrouEm(AGORA);
        List<AgregadoDTO> porLoja = List.of(new AgregadoDTO(UUID.randomUUID(), "Centro", 2L, 30L));
        List<AgregadoDTO> porPromotor = List.of(new AgregadoDTO(UUID.randomUUID(), "Ana", 2L, 30L));
        List<AgregadoDTO> porProduto = List.of(new AgregadoDTO(UUID.randomUUID(), "Sabonete", 2L, 30L));
        when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        when(reposicaoRepository.totaisDaMarca(marca.getId(), inicio, FIM_PADRAO)).thenReturn(new TotaisDTO(2L, 30L));
        when(reposicaoRepository.porLojaDaMarca(marca.getId(), inicio, FIM_PADRAO)).thenReturn(porLoja);
        when(reposicaoRepository.porAutorDaMarca(marca.getId(), inicio, FIM_PADRAO)).thenReturn(porPromotor);
        when(reposicaoRepository.porProdutoDaMarca(marca.getId(), inicio, FIM_PADRAO)).thenReturn(porProduto);
        when(promotorRepository.listarSemAtividade(marca.getId(), inicio, FIM_PADRAO)).thenReturn(List.of(promotor));
        stubUso(marca, 1, 3, 2, 0);

        PainelMarcaDTO painel = service.painelDaMarca(gestor, marca.getId(), null, null);

        assertThat(painel.marcaId()).isEqualTo(marca.getId());
        assertThat(painel.marcaNome()).isEqualTo("Marca Alfa");
        assertThat(painel.totais()).isEqualTo(new TotaisDTO(2L, 30L));
        assertThat(painel.porLoja()).isEqualTo(porLoja);
        assertThat(painel.porPromotor()).isEqualTo(porPromotor);
        assertThat(painel.porProduto()).isEqualTo(porProduto);
        assertThat(painel.promotoresSemAtividade()).extracting("nome").containsExactly("Sem Atividade");
        assertThat(painel.uso().promotores()).isEqualTo(2);
    }

    @Test
    void painelDaMarcaDeOutroGestorLancaNaoEncontradoENaoAgrega() {
        UUID alheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(alheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.painelDaMarca(gestor, alheia, null, null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).totaisDaMarca(any(), any(), any());
    }

    @Test
    void painelDaMarcaComPeriodoInvalidoLancaErro() {
        when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        Instant de = Instant.parse("2026-03-01T00:00:00Z");

        assertThatThrownBy(() -> service.painelDaMarca(gestor, marca.getId(), de, de.minusSeconds(5)))
                .satisfies(PainelServiceTests::assertPeriodoInvalido);
    }

    @Test
    void painelDasMinhasReposicoesAgregaPorMarcaLojaProdutoEMostraAsUltimas() {
        Instant inicio = FIM_PADRAO.minus(Duration.ofDays(30));
        List<AgregadoDTO> porMarca = List.of(new AgregadoDTO(marca.getId(), "Marca Alfa", 4L, 50L));
        List<AgregadoDTO> porLoja = List.of(new AgregadoDTO(UUID.randomUUID(), "Centro", 4L, 50L));
        List<AgregadoDTO> porProduto = List.of(new AgregadoDTO(UUID.randomUUID(), "Sabonete", 4L, 50L));
        List<ReposicaoDTO> ultimas = List.of();
        when(reposicaoRepository.totaisDoAutor(gestor.getId(), inicio, FIM_PADRAO)).thenReturn(new TotaisDTO(4L, 50L));
        when(reposicaoRepository.porMarcaDoAutor(gestor.getId(), inicio, FIM_PADRAO)).thenReturn(porMarca);
        when(reposicaoRepository.porLojaDoAutor(gestor.getId(), inicio, FIM_PADRAO)).thenReturn(porLoja);
        when(reposicaoRepository.porProdutoDoAutor(gestor.getId(), inicio, FIM_PADRAO)).thenReturn(porProduto);
        when(reposicaoRepository.existsByAutorId(gestor.getId())).thenReturn(true);

        when(reposicaoService.ultimasDoUsuario(gestor)).thenReturn(ultimas);

        PainelAutorDTO painel = service.painelDasMinhasReposicoes(gestor, null, null);

        assertThat(painel.possuiReposicoes()).isTrue();
        assertThat(painel.totais()).isEqualTo(new TotaisDTO(4L, 50L));
        assertThat(painel.porMarca()).isEqualTo(porMarca);
        assertThat(painel.porLoja()).isEqualTo(porLoja);
        assertThat(painel.porProduto()).isEqualTo(porProduto);
        assertThat(painel.ultimas()).isSameAs(ultimas);
    }

    @Test
    void possuiReposicoesConsideraQualquerDataENaoOPeriodoConsultado() {
        when(reposicaoRepository.totaisDoAutor(any(), any(), any())).thenReturn(new TotaisDTO(0L, 0L));
        when(reposicaoRepository.existsByAutorId(gestor.getId())).thenReturn(true);

        PainelAutorDTO painel = service.painelDasMinhasReposicoes(gestor, null, null);

        assertThat(painel.totais().reposicoes()).isZero();
        assertThat(painel.possuiReposicoes()).isTrue();
    }

    @Test
    void usuarioQueNuncaRegistrouReposicaoNaoPossuiReposicoes() {
        when(reposicaoRepository.totaisDoAutor(any(), any(), any())).thenReturn(new TotaisDTO(0L, 0L));
        when(reposicaoRepository.existsByAutorId(gestor.getId())).thenReturn(false);

        assertThat(service.painelDasMinhasReposicoes(gestor, null, null).possuiReposicoes()).isFalse();
    }

    @Test
    void painelDasMinhasReposicoesComPeriodoInvalidoLancaErroSemConsultar() {
        Instant de = Instant.parse("2026-03-01T00:00:00Z");

        assertThatThrownBy(() -> service.painelDasMinhasReposicoes(gestor, de, de))
                .satisfies(PainelServiceTests::assertPeriodoInvalido);

        verify(reposicaoRepository, never()).totaisDoAutor(any(), any(), any());
    }
}
