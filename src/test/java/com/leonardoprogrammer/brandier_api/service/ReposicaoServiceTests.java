package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.ItemReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService.ItemEntrada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReposicaoServiceTests {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ReposicaoRepository reposicaoRepository;

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MarcaAcessoService acessoService;

    private ReposicaoService service;
    private Usuario gestor;
    private Usuario promotor;
    private Usuario outroPromotor;
    private Marca marca;
    private Loja loja;
    private Produto sabonete;
    private Produto shampoo;
    private Produto antigo;

    @BeforeEach
    void preparar() {
        service = new ReposicaoService(reposicaoRepository, lojaRepository, produtoRepository, acessoService,
                Clock.fixed(AGORA, ZoneOffset.UTC), 24);
        gestor = usuario("Gestor");
        promotor = usuario("Promotora");
        outroPromotor = usuario("Outro");
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
        loja = new Loja();
        loja.setId(UUID.randomUUID());
        loja.setNome("Loja Centro");
        loja.setMarca(marca);
        sabonete = produto("Sabonete", true);
        shampoo = produto("Shampoo", true);
        antigo = produto("Antigo", false);

        lenient().when(acessoService.exigir(gestor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, true));
        lenient().when(acessoService.exigir(promotor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, false));
        lenient().when(acessoService.exigir(outroPromotor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, false));
        lenient().when(lojaRepository.findByIdAndMarcaId(loja.getId(), marca.getId())).thenReturn(Optional.of(loja));
        lenient().when(produtoRepository.findAllByIdInAndMarcaId(anyCollection(), eq(marca.getId()))).thenAnswer(invocacao -> {
            Collection<UUID> ids = invocacao.getArgument(0);
            return List.of(sabonete, shampoo, antigo).stream().filter(p -> ids.contains(p.getId())).toList();
        });
        lenient().when(reposicaoRepository.save(any(Reposicao.class))).thenAnswer(invocacao -> {
            Reposicao reposicao = invocacao.getArgument(0);
            if (reposicao.getId() == null) {
                reposicao.setId(UUID.randomUUID());
            }
            return reposicao;
        });
    }

    private static Usuario usuario(String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        return usuario;
    }

    private static Produto produto(String nome, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setAtivo(ativo);
        return produto;
    }

    private Reposicao existente(Usuario autor, Instant criadoEm, ItemEntrada... itens) {
        Reposicao reposicao = new Reposicao();
        reposicao.setId(UUID.randomUUID());
        reposicao.setMarca(marca);
        reposicao.setLoja(loja);
        reposicao.setAutor(autor);
        reposicao.setRepostoEm(criadoEm);
        reposicao.setCriadoEm(criadoEm);
        reposicao.setItens(new ArrayList<>());
        for (ItemEntrada entrada : itens) {
            ItemReposicao item = new ItemReposicao();
            item.setReposicao(reposicao);
            item.setProduto(List.of(sabonete, shampoo, antigo).stream().filter(p -> p.getId().equals(entrada.produtoId())).findFirst().orElseThrow());
            item.setQuantidade(entrada.quantidade());
            reposicao.getItens().add(item);
        }
        lenient().when(reposicaoRepository.findByIdAndMarcaId(reposicao.getId(), marca.getId())).thenReturn(Optional.of(reposicao));
        return reposicao;
    }

    private ItemEntrada item(Produto produto, int quantidade) {
        return new ItemEntrada(produto.getId(), quantidade);
    }

    private static void assertErro(Throwable erro, ErrorCode esperado) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(esperado));
    }

    @Test
    void promotorCriaReposicaoComDataAgoraItensEObservacoesAparadas() {
        ReposicaoDTO criada = service.criar(promotor, marca.getId(), loja.getId(), null,
                List.of(item(sabonete, 10), item(shampoo, 5)), "  Gôndola cheia  ");

        ArgumentCaptor<Reposicao> salva = ArgumentCaptor.forClass(Reposicao.class);
        verify(reposicaoRepository).save(salva.capture());
        assertThat(salva.getValue().getMarca()).isSameAs(marca);
        assertThat(salva.getValue().getLoja()).isSameAs(loja);
        assertThat(salva.getValue().getAutor()).isSameAs(promotor);
        assertThat(salva.getValue().getRepostoEm()).isEqualTo(AGORA);
        assertThat(salva.getValue().getCriadoEm()).isEqualTo(AGORA);
        assertThat(salva.getValue().getObservacoes()).isEqualTo("Gôndola cheia");
        assertThat(salva.getValue().getItens()).hasSize(2).allSatisfy(item -> assertThat(item.getReposicao()).isSameAs(salva.getValue()));

        assertThat(criada.marcaNome()).isEqualTo("Marca Alfa");
        assertThat(criada.lojaNome()).isEqualTo("Loja Centro");
        assertThat(criada.autorNome()).isEqualTo("Promotora");
        assertThat(criada.totalUnidades()).isEqualTo(15);
        assertThat(criada.itens()).extracting(ItemReposicaoDTO::produtoNome).containsExactly("Sabonete", "Shampoo");
        assertThat(criada.podeEditar()).isTrue();
        assertThat(criada.editavelAte()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
    }

    @Test
    void gestorCriaReposicaoNaPropriaMarcaSemPrazoDeEdicao() {
        ReposicaoDTO criada = service.criar(gestor, marca.getId(), loja.getId(), null, List.of(item(sabonete, 1)), null);

        assertThat(criada.podeEditar()).isTrue();
        assertThat(criada.editavelAte()).isNull();
        assertThat(criada.observacoes()).isNull();
    }

    @Test
    void observacoesEmBrancoViramNulas() {
        service.criar(promotor, marca.getId(), loja.getId(), null, List.of(item(sabonete, 1)), "   ");

        ArgumentCaptor<Reposicao> salva = ArgumentCaptor.forClass(Reposicao.class);
        verify(reposicaoRepository).save(salva.capture());
        assertThat(salva.getValue().getObservacoes()).isNull();
    }

    @Test
    void criarGuardaADataInformadaNoPassado() {
        Instant ontem = AGORA.minus(Duration.ofDays(1));

        service.criar(promotor, marca.getId(), loja.getId(), ontem, List.of(item(sabonete, 1)), null);

        ArgumentCaptor<Reposicao> salva = ArgumentCaptor.forClass(Reposicao.class);
        verify(reposicaoRepository).save(salva.capture());
        assertThat(salva.getValue().getRepostoEm()).isEqualTo(ontem);
        assertThat(salva.getValue().getCriadoEm()).isEqualTo(AGORA);
    }

    @Test
    void criarAceitaDataNoFuturoDentroDaToleranciaDeRelogio() {
        assertThat(service.criar(promotor, marca.getId(), loja.getId(), AGORA.plusSeconds(240), List.of(item(sabonete, 1)), null)).isNotNull();
    }

    @Test
    void criarRejeitaDataNoFuturoAlemDaTolerancia() {
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), AGORA.plusSeconds(301), List.of(item(sabonete, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.DATA_REPOSICAO_FUTURA));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void criarSemItensLancaReposicaoSemItens() {
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, List.of(), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.REPOSICAO_SEM_ITENS));
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.REPOSICAO_SEM_ITENS));
    }

    @Test
    void criarComQuantidadeNaoPositivaLancaQuantidadeInvalida() {
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, List.of(item(sabonete, 0)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.QUANTIDADE_INVALIDA));
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, List.of(item(sabonete, -3)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.QUANTIDADE_INVALIDA));
    }

    @Test
    void criarComOMesmoProdutoDuasVezesLancaProdutoRepetido() {
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null,
                List.of(item(sabonete, 1), item(sabonete, 2)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_REPETIDO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void criarComProdutoDeOutraMarcaLancaProdutoInvalido() {
        UUID deOutraMarca = UUID.randomUUID();

        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null,
                List.of(item(sabonete, 1), new ItemEntrada(deOutraMarca, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_INVALIDO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void criarComProdutoInativoLancaProdutoInvalido() {
        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, List.of(item(antigo, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_INVALIDO));
    }

    @Test
    void criarComLojaDeOutraMarcaLancaNaoEncontrado() {
        UUID lojaAlheia = UUID.randomUUID();
        when(lojaRepository.findByIdAndMarcaId(lojaAlheia, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), lojaAlheia, null, List.of(item(sabonete, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void criarSemAcessoAMarcaLancaNaoEncontradoENaoSalva() {
        Usuario estranho = usuario("Estranho");
        when(acessoService.exigir(estranho, marca.getId())).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        assertThatThrownBy(() -> service.criar(estranho, marca.getId(), loja.getId(), null, List.of(item(sabonete, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void criarEmMarcaInativaLancaMarcaInativaENaoSalva() {
        marca.setAtiva(false);

        assertThatThrownBy(() -> service.criar(promotor, marca.getId(), loja.getId(), null, List.of(item(sabonete, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.MARCA_INATIVA));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void promotorAtualizaAPropriaReposicaoDentroDaJanelaTrocandoRemovendoEAdicionandoItens() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(23)), item(sabonete, 10), item(antigo, 3));
        Loja outraLoja = new Loja();
        outraLoja.setId(UUID.randomUUID());
        outraLoja.setNome("Loja Norte");
        outraLoja.setMarca(marca);
        when(lojaRepository.findByIdAndMarcaId(outraLoja.getId(), marca.getId())).thenReturn(Optional.of(outraLoja));

        ReposicaoDTO atualizada = service.atualizar(promotor, marca.getId(), reposicao.getId(), outraLoja.getId(), null,
                List.of(item(sabonete, 7), item(shampoo, 2)), "Ajuste");

        assertThat(reposicao.getLoja()).isSameAs(outraLoja);
        assertThat(reposicao.getObservacoes()).isEqualTo("Ajuste");
        assertThat(reposicao.getItens()).hasSize(2);
        assertThat(reposicao.getItens()).extracting(i -> i.getProduto().getNome()).containsExactlyInAnyOrder("Sabonete", "Shampoo");
        assertThat(reposicao.getItens()).filteredOn(i -> i.getProduto() == sabonete).extracting(ItemReposicao::getQuantidade).containsExactly(7);
        assertThat(atualizada.totalUnidades()).isEqualTo(9);
        assertThat(atualizada.lojaNome()).isEqualTo("Loja Norte");
        verify(reposicaoRepository).save(reposicao);
    }

    @Test
    void atualizarMantemOMesmoItemEntidadeAoApenasMudarAQuantidade() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(1)), item(sabonete, 10));
        ItemReposicao original = reposicao.getItens().get(0);

        service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null, List.of(item(sabonete, 4)), null);

        assertThat(reposicao.getItens()).containsExactly(original);
        assertThat(original.getQuantidade()).isEqualTo(4);
    }

    @Test
    void atualizarPermiteManterProdutoQueFicouInativoDepoisDeRegistrado() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(1)), item(antigo, 3));

        ReposicaoDTO atualizada = service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(antigo, 8)), null);

        assertThat(atualizada.totalUnidades()).isEqualTo(8);
    }

    @Test
    void atualizarNaoPermiteAdicionarProdutoInativoNovo() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(1)), item(sabonete, 3));

        assertThatThrownBy(() -> service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(sabonete, 3), item(antigo, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_INVALIDO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void promotorNaoAtualizaForaDaJanelaDe24Horas() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(25)), item(sabonete, 1));

        assertThatThrownBy(() -> service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(sabonete, 2)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.JANELA_EDICAO_ENCERRADA));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void aJanelaTerminaExatamenteAs24HorasDaCriacao() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(24)), item(sabonete, 1));

        assertThatThrownBy(() -> service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(sabonete, 2)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.JANELA_EDICAO_ENCERRADA));
    }

    @Test
    void aJanelaContaDaCriacaoENaoDaDataInformadaDaReposicao() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(1)), item(sabonete, 1));
        reposicao.setRepostoEm(AGORA.minus(Duration.ofDays(30)));

        assertThat(service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), AGORA.minus(Duration.ofDays(30)),
                List.of(item(sabonete, 2)), null)).isNotNull();
    }

    @Test
    void promotorNaoAtualizaReposicaoDeOutroPromotorENaoSabeQueExiste() {
        Reposicao reposicao = existente(outroPromotor, AGORA.minus(Duration.ofHours(1)), item(sabonete, 1));

        assertThatThrownBy(() -> service.atualizar(promotor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(sabonete, 2)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).save(any());
    }

    @Test
    void gestorAtualizaQualquerReposicaoDaMarcaMesmoDepoisDaJanela() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofDays(40)), item(sabonete, 1));

        ReposicaoDTO atualizada = service.atualizar(gestor, marca.getId(), reposicao.getId(), loja.getId(), null,
                List.of(item(shampoo, 6)), null);

        assertThat(atualizada.totalUnidades()).isEqualTo(6);
        assertThat(atualizada.podeEditar()).isTrue();
        assertThat(atualizada.editavelAte()).isNull();
    }

    @Test
    void atualizarReposicaoInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(reposicaoRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(gestor, marca.getId(), id, loja.getId(), null, List.of(item(sabonete, 1)), null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void promotorExcluiAPropriaReposicaoDentroDaJanela() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(2)), item(sabonete, 1));

        service.excluir(promotor, marca.getId(), reposicao.getId());

        verify(reposicaoRepository).delete(reposicao);
    }

    @Test
    void promotorNaoExcluiForaDaJanela() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(30)), item(sabonete, 1));

        assertThatThrownBy(() -> service.excluir(promotor, marca.getId(), reposicao.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.JANELA_EDICAO_ENCERRADA));

        verify(reposicaoRepository, never()).delete(any(Reposicao.class));
    }

    @Test
    void promotorNaoExcluiReposicaoDeOutro() {
        Reposicao reposicao = existente(outroPromotor, AGORA.minus(Duration.ofHours(2)), item(sabonete, 1));

        assertThatThrownBy(() -> service.excluir(promotor, marca.getId(), reposicao.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(reposicaoRepository, never()).delete(any(Reposicao.class));
    }

    @Test
    void gestorExcluiQualquerReposicaoDaMarca() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofDays(90)), item(sabonete, 1));

        service.excluir(gestor, marca.getId(), reposicao.getId());

        verify(reposicaoRepository).delete(reposicao);
    }

    @Test
    void buscarMostraAReposicaoAoGestorEAoAutorMasNaoAOutroPromotor() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(1)), item(sabonete, 1));

        assertThat(service.buscar(gestor, marca.getId(), reposicao.getId()).id()).isEqualTo(reposicao.getId());
        assertThat(service.buscar(promotor, marca.getId(), reposicao.getId()).id()).isEqualTo(reposicao.getId());
        assertThatThrownBy(() -> service.buscar(outroPromotor, marca.getId(), reposicao.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void buscarIndicaQueOPromotorNaoPodeMaisEditarDepoisDaJanela() {
        Reposicao reposicao = existente(promotor, AGORA.minus(Duration.ofHours(48)), item(sabonete, 1));

        ReposicaoDTO dto = service.buscar(promotor, marca.getId(), reposicao.getId());

        assertThat(dto.podeEditar()).isFalse();
        assertThat(dto.editavelAte()).isEqualTo(reposicao.getCriadoEm().plus(Duration.ofHours(24)));
    }

    @Test
    void listarDaMarcaParaOGestorDevolveTodasOrdenadasDaMaisRecente() {
        Reposicao primeira = existente(promotor, AGORA.minusSeconds(10), item(sabonete, 1));
        when(reposicaoRepository.findAllByMarcaId(eq(marca.getId()), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<>(List.of(primeira), invocacao.getArgument(1), 41));

        PaginaDTO<ReposicaoDTO> pagina = service.listarDaMarca(gestor, marca.getId(), 1, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reposicaoRepository).findAllByMarcaId(eq(marca.getId()), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("repostoEm"), Sort.Order.desc("criadoEm")));
        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamanho()).isEqualTo(20);
        assertThat(pagina.totalElementos()).isEqualTo(41);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        verify(reposicaoRepository, never()).findAllByMarcaIdAndAutorId(any(), any(), any());
    }

    @Test
    void listarDaMarcaParaOPromotorDevolveSoAsProprias() {
        when(reposicaoRepository.findAllByMarcaIdAndAutorId(eq(marca.getId()), eq(promotor.getId()), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<Reposicao>(List.of(), invocacao.getArgument(2), 0));

        PaginaDTO<ReposicaoDTO> pagina = service.listarDaMarca(promotor, marca.getId(), 0, 20);

        assertThat(pagina.conteudo()).isEmpty();
        verify(reposicaoRepository, never()).findAllByMarcaId(any(), any());
    }

    @Test
    void listarDaMarcaSemAcessoLancaNaoEncontrado() {
        Usuario estranho = usuario("Estranho");
        when(acessoService.exigir(estranho, marca.getId())).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        assertThatThrownBy(() -> service.listarDaMarca(estranho, marca.getId(), 0, 20))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void listarLimitaOTamanhoDaPaginaEIgnoraValoresNegativos() {
        when(reposicaoRepository.findAllByMarcaId(eq(marca.getId()), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<Reposicao>(List.of(), invocacao.getArgument(1), 0));

        service.listarDaMarca(gestor, marca.getId(), -3, 5000);
        service.listarDaMarca(gestor, marca.getId(), 2, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reposicaoRepository, org.mockito.Mockito.times(2)).findAllByMarcaId(eq(marca.getId()), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isEqualTo(2);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void ultimasDoUsuarioDevolveAsCincoMaisRecentesDoAutor() {
        Reposicao propria = existente(promotor, AGORA.minus(Duration.ofHours(2)), item(sabonete, 4));
        when(reposicaoRepository.findTop5ByAutorIdOrderByRepostoEmDescCriadoEmDesc(promotor.getId())).thenReturn(List.of(propria));

        List<ReposicaoDTO> ultimas = service.ultimasDoUsuario(promotor);

        assertThat(ultimas).hasSize(1);
        assertThat(ultimas.get(0).totalUnidades()).isEqualTo(4);
        assertThat(ultimas.get(0).podeEditar()).isTrue();
    }

    @Test
    void listarMinhasBuscaPeloAutorEmTodasAsMarcas() {
        Reposicao propria = existente(promotor, AGORA.minus(Duration.ofHours(30)), item(sabonete, 4));
        when(reposicaoRepository.findAllByAutorId(eq(promotor.getId()), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<>(List.of(propria), invocacao.getArgument(1), 1));

        PaginaDTO<ReposicaoDTO> pagina = service.listarMinhas(promotor, 0, 20);

        assertThat(pagina.conteudo()).hasSize(1);
        assertThat(pagina.conteudo().get(0).totalUnidades()).isEqualTo(4);
        assertThat(pagina.conteudo().get(0).podeEditar()).isFalse();
    }
}
