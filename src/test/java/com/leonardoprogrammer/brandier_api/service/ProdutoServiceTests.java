package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ItemReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTests {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private MarcaAcessoService acessoService;

    @Mock
    private ItemReposicaoRepository itemReposicaoRepository;

    private ProdutoService produtoService;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        produtoService = new ProdutoService(produtoRepository, marcaRepository, acessoService, itemReposicaoRepository,
                new LimitsProperties(2, 5, 10, 5, 500));
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setGestor(gestor);
        marca.setNome("Marca");
        lenient().when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        lenient().when(marcaRepository.findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId()))
                .thenReturn(Optional.of(marca));
        lenient().when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private Produto produto(String nome) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setMarca(marca);
        produto.setNome(nome);
        return produto;
    }

    private static void assertErro(Throwable erro, ErrorCode esperado) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(esperado));
    }

    @Test
    void limiteDeProdutosVemDaConfiguracao() {
        assertThat(produtoService.limiteDeProdutos()).isEqualTo(10);
    }

    @Test
    void criarNormalizaNomeECodigoEVinculaOProdutoAMarca() {
        when(produtoRepository.countByMarcaIdAndAtivoTrue(marca.getId())).thenReturn(0L);

        Produto criado = produtoService.criar(gestor, marca.getId(), "  Sabonete  ", "  SKU-1 ", "#4F46E5");

        ArgumentCaptor<Produto> capturado = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository).save(capturado.capture());
        assertThat(criado.getNome()).isEqualTo("Sabonete");
        assertThat(capturado.getValue().getCodigo()).isEqualTo("SKU-1");
        assertThat(capturado.getValue().getCor()).isEqualTo("#4f46e5");
        assertThat(capturado.getValue().getMarca()).isSameAs(marca);
        assertThat(capturado.getValue().isAtivo()).isTrue();
    }

    @Test
    void criarGuardaCodigoECorEmBrancoComoNulo() {
        assertThat(produtoService.criar(gestor, marca.getId(), "Sabonete", "   ", "   ").getCodigo()).isNull();
        assertThat(produtoService.criar(gestor, marca.getId(), "Shampoo", null, null).getCor()).isNull();
    }

    @Test
    void criarTravaALinhaDaMarcaAntesDeContarOsProdutosAtivos() {
        produtoService.criar(gestor, marca.getId(), "Sabonete", null, null);

        InOrder ordem = inOrder(marcaRepository, produtoRepository);
        ordem.verify(marcaRepository).findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId());
        ordem.verify(produtoRepository).countByMarcaIdAndAtivoTrue(marca.getId());
        ordem.verify(produtoRepository).save(any(Produto.class));
    }

    @Test
    void criarPermiteACriacaoDoUltimoProdutoDentroDoLimite() {
        when(produtoRepository.countByMarcaIdAndAtivoTrue(marca.getId())).thenReturn(9L);

        assertThat(produtoService.criar(gestor, marca.getId(), "Décimo", null, null)).isNotNull();
    }

    @Test
    void criarNoLimiteDeProdutosAtivosLancaLimiteAtingidoENaoSalva() {
        when(produtoRepository.countByMarcaIdAndAtivoTrue(marca.getId())).thenReturn(10L);

        assertThatThrownBy(() -> produtoService.criar(gestor, marca.getId(), "Décimo primeiro", null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.LIMITE_PRODUTOS_ATINGIDO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void criarComNomeJaUsadoNaMarcaIgnorandoMaiusculasLancaNomeDuplicado() {
        when(produtoRepository.existsByMarcaIdAndNomeIgnoreCase(marca.getId(), "Sabonete")).thenReturn(true);

        assertThatThrownBy(() -> produtoService.criar(gestor, marca.getId(), " Sabonete ", null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_NOME_DUPLICADO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void criarEmMarcaDeOutroGestorLancaNaoEncontradoENaoConta() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.criar(gestor, marcaAlheia, "Sabonete", null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(produtoRepository, never()).countByMarcaIdAndAtivoTrue(any());
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void listarParaOGestorDevolveTodosOsProdutosInclusiveInativos() {
        List<Produto> produtos = List.of(produto("A"), produto("B"));
        when(acessoService.exigir(gestor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, true));
        when(produtoRepository.findAllByMarcaIdOrderByNomeAsc(marca.getId())).thenReturn(produtos);

        assertThat(produtoService.listar(gestor, marca.getId())).isEqualTo(produtos);
    }

    @Test
    void listarParaOPromotorDevolveSomenteProdutosAtivos() {
        Usuario promotor = new Usuario();
        promotor.setId(UUID.randomUUID());
        List<Produto> ativos = List.of(produto("A"));
        when(acessoService.exigir(promotor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, false));
        when(produtoRepository.findAllByMarcaIdAndAtivoTrueOrderByNomeAsc(marca.getId())).thenReturn(ativos);

        assertThat(produtoService.listar(promotor, marca.getId())).isEqualTo(ativos);

        verify(produtoRepository, never()).findAllByMarcaIdOrderByNomeAsc(any());
    }

    @Test
    void listarSemAcessoALancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(acessoService.exigir(gestor, marcaAlheia)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        assertThatThrownBy(() -> produtoService.listar(gestor, marcaAlheia))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void buscarDevolveOProdutoDaMarca() {
        Produto existente = produto("A");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        assertThat(produtoService.buscar(gestor, marca.getId(), existente.getId())).isSameAs(existente);
    }

    @Test
    void buscarProdutoDeOutraMarcaLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.buscar(gestor, marca.getId(), id))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void atualizarAlteraOsCamposNormalizadosEMantemOStatusQuandoAtivoNaoVem() {
        Produto existente = produto("Antigo");
        existente.setAtivo(false);
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        Produto atualizado = produtoService.atualizar(gestor, marca.getId(), existente.getId(), " Novo ", " COD-2 ", " #4F46E5 ", null);

        assertThat(atualizado.getNome()).isEqualTo("Novo");
        assertThat(atualizado.getCodigo()).isEqualTo("COD-2");
        assertThat(atualizado.getCor()).isEqualTo("#4f46e5");
        assertThat(atualizado.isAtivo()).isFalse();
    }

    @Test
    void atualizarComNomeDeOutroProdutoDaMarcaLancaNomeDuplicado() {
        Produto existente = produto("Antigo");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(produtoRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marca.getId(), "Outro", existente.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Outro", null, null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_NOME_DUPLICADO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void atualizarProdutoInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.atualizar(gestor, marca.getId(), id, "Nome", null, null, null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void atualizarReativaProdutoInativoQuandoHaVaga() {
        Produto existente = produto("Antigo");
        existente.setAtivo(false);
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(produtoRepository.countByMarcaIdAndAtivoTrue(marca.getId())).thenReturn(9L);

        assertThat(produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Antigo", null, null, true).isAtivo()).isTrue();
    }

    @Test
    void atualizarNaoReativaProdutoQuandoOLimiteDeAtivosFoiAtingido() {
        Produto existente = produto("Antigo");
        existente.setAtivo(false);
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(produtoRepository.countByMarcaIdAndAtivoTrue(marca.getId())).thenReturn(10L);

        assertThatThrownBy(() -> produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Antigo", null, null, true))
                .satisfies(erro -> assertErro(erro, ErrorCode.LIMITE_PRODUTOS_ATINGIDO));

        assertThat(existente.isAtivo()).isFalse();
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void atualizarProdutoJaAtivoNaoConsultaOLimite() {
        Produto existente = produto("Antigo");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Antigo", null, null, true);

        verify(produtoRepository, never()).countByMarcaIdAndAtivoTrue(any());
    }

    @Test
    void atualizarPodeInativarOProduto() {
        Produto existente = produto("Antigo");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        assertThat(produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Antigo", null, null, false).isAtivo()).isFalse();
    }

    @Test
    void excluirProdutoSemReposicoesRemoveDeVerdade() {
        Produto existente = produto("A");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(itemReposicaoRepository.existsByProdutoId(existente.getId())).thenReturn(false);

        assertThat(produtoService.excluir(gestor, marca.getId(), existente.getId())).isEmpty();

        verify(produtoRepository).delete(existente);
    }

    @Test
    void excluirProdutoComReposicoesApenasInativaEDevolveOProduto() {
        Produto existente = produto("A");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(itemReposicaoRepository.existsByProdutoId(existente.getId())).thenReturn(true);

        Optional<Produto> resultado = produtoService.excluir(gestor, marca.getId(), existente.getId());

        assertThat(resultado).containsSame(existente);
        assertThat(existente.isAtivo()).isFalse();
        verify(produtoRepository, never()).delete(any());
    }

    @Test
    void excluirEmMarcaDeOutroGestorLancaNaoEncontradoENaoRemove() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.excluir(gestor, marcaAlheia, UUID.randomUUID()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(produtoRepository, never()).delete(any());
    }
}
