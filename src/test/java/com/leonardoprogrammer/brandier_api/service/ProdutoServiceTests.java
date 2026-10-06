package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
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

    private ProdutoService produtoService;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        produtoService = new ProdutoService(produtoRepository, marcaRepository, new LimitsProperties(2, 5, 10));
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
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        Produto criado = produtoService.criar(gestor, marca.getId(), "  Sabonete  ", "  SKU-1 ");

        ArgumentCaptor<Produto> capturado = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository).save(capturado.capture());
        assertThat(criado.getNome()).isEqualTo("Sabonete");
        assertThat(capturado.getValue().getCodigo()).isEqualTo("SKU-1");
        assertThat(capturado.getValue().getMarca()).isSameAs(marca);
    }

    @Test
    void criarGuardaCodigoEmBrancoComoNulo() {
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        assertThat(produtoService.criar(gestor, marca.getId(), "Sabonete", "   ").getCodigo()).isNull();
        assertThat(produtoService.criar(gestor, marca.getId(), "Shampoo", null).getCodigo()).isNull();
    }

    @Test
    void criarTravaALinhaDaMarcaAntesDeContarOsProdutos() {
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        produtoService.criar(gestor, marca.getId(), "Sabonete", null);

        InOrder ordem = inOrder(marcaRepository, produtoRepository);
        ordem.verify(marcaRepository).findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId());
        ordem.verify(produtoRepository).countByMarcaId(marca.getId());
        ordem.verify(produtoRepository).save(any(Produto.class));
    }

    @Test
    void criarPermiteACriacaoDoUltimoProdutoDentroDoLimite() {
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(9L);

        assertThat(produtoService.criar(gestor, marca.getId(), "Décimo", null)).isNotNull();
    }

    @Test
    void criarNoLimiteLancaLimiteAtingidoENaoSalva() {
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(10L);

        assertThatThrownBy(() -> produtoService.criar(gestor, marca.getId(), "Décimo primeiro", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.LIMITE_PRODUTOS_ATINGIDO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void criarComNomeJaUsadoNaMarcaIgnorandoMaiusculasLancaNomeDuplicado() {
        when(produtoRepository.countByMarcaId(marca.getId())).thenReturn(1L);
        when(produtoRepository.existsByMarcaIdAndNomeIgnoreCase(marca.getId(), "Sabonete")).thenReturn(true);

        assertThatThrownBy(() -> produtoService.criar(gestor, marca.getId(), " Sabonete ", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_NOME_DUPLICADO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void criarEmMarcaDeOutroGestorLancaNaoEncontradoENaoConta() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.criar(gestor, marcaAlheia, "Sabonete", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(produtoRepository, never()).countByMarcaId(any());
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void listarDevolveOsProdutosDaMarcaDoGestor() {
        List<Produto> produtos = List.of(produto("A"), produto("B"));
        when(produtoRepository.findAllByMarcaIdOrderByNomeAsc(marca.getId())).thenReturn(produtos);

        assertThat(produtoService.listar(gestor, marca.getId())).isEqualTo(produtos);
    }

    @Test
    void listarMarcaDeOutroGestorLancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.listar(gestor, marcaAlheia))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(produtoRepository, never()).findAllByMarcaIdOrderByNomeAsc(any());
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
    void atualizarAlteraOsCamposNormalizados() {
        Produto existente = produto("Antigo");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        Produto atualizado = produtoService.atualizar(gestor, marca.getId(), existente.getId(), " Novo ", " COD-2 ");

        assertThat(atualizado.getNome()).isEqualTo("Novo");
        assertThat(atualizado.getCodigo()).isEqualTo("COD-2");
    }

    @Test
    void atualizarComNomeDeOutroProdutoDaMarcaLancaNomeDuplicado() {
        Produto existente = produto("Antigo");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(produtoRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marca.getId(), "Outro", existente.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> produtoService.atualizar(gestor, marca.getId(), existente.getId(), "Outro", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.PRODUTO_NOME_DUPLICADO));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void atualizarProdutoInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.atualizar(gestor, marca.getId(), id, "Nome", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void excluirRemoveOProdutoDaMarcaDoGestor() {
        Produto existente = produto("A");
        when(produtoRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        produtoService.excluir(gestor, marca.getId(), existente.getId());

        verify(produtoRepository).delete(existente);
    }

    @Test
    void excluirEmMarcaDeOutroGestorLancaNaoEncontradoENaoRemove() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.excluir(gestor, marcaAlheia, UUID.randomUUID()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(produtoRepository, never()).delete(any());
    }
}
