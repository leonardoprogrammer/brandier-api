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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final MarcaRepository marcaRepository;
    private final MarcaAcessoService acessoService;
    private final ItemReposicaoRepository itemReposicaoRepository;
    private final LimitsProperties limits;

    public ProdutoService(ProdutoRepository produtoRepository,
                          MarcaRepository marcaRepository,
                          MarcaAcessoService acessoService,
                          ItemReposicaoRepository itemReposicaoRepository,
                          LimitsProperties limits) {
        this.produtoRepository = produtoRepository;
        this.marcaRepository = marcaRepository;
        this.acessoService = acessoService;
        this.itemReposicaoRepository = itemReposicaoRepository;
        this.limits = limits;
    }

    public int limiteDeProdutos() {
        return limits.maxProductsPerBrand();
    }

    @Transactional(readOnly = true)
    public List<Produto> listar(Usuario usuario, UUID marcaId) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        if (acesso.gestor()) {
            return produtoRepository.findAllByMarcaIdOrderByNomeAsc(marcaId);
        }
        return produtoRepository.findAllByMarcaIdAndAtivoTrueOrderByNomeAsc(marcaId);
    }

    @Transactional(readOnly = true)
    public Produto buscar(Usuario gestor, UUID marcaId, UUID id) {
        buscarMarca(gestor, marcaId);
        return buscarProduto(marcaId, id);
    }

    @Transactional
    public Produto criar(Usuario gestor, UUID marcaId, String nome, String codigo, String cor) {
        Marca marca = marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId())
                .orElseThrow(ProdutoService::naoEncontrado);

        if (produtoRepository.countByMarcaIdAndAtivoTrue(marcaId) >= limits.maxProductsPerBrand()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_PRODUTOS_ATINGIDO);
        }

        String nomeNormalizado = nome.trim();
        if (produtoRepository.existsByMarcaIdAndNomeIgnoreCase(marcaId, nomeNormalizado)) {
            throw new BusinessRuleException(ErrorCode.PRODUTO_NOME_DUPLICADO);
        }

        Produto produto = new Produto();
        produto.setMarca(marca);
        produto.setNome(nomeNormalizado);
        produto.setCodigo(normalizarCodigo(codigo));
        produto.setCor(normalizarCor(cor));
        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto atualizar(Usuario gestor, UUID marcaId, UUID id, String nome, String codigo, String cor, Boolean ativo) {
        marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId()).orElseThrow(ProdutoService::naoEncontrado);
        Produto produto = buscarProduto(marcaId, id);

        String nomeNormalizado = nome.trim();
        if (produtoRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marcaId, nomeNormalizado, id)) {
            throw new BusinessRuleException(ErrorCode.PRODUTO_NOME_DUPLICADO);
        }

        if (ativo != null && ativo && !produto.isAtivo()
                && produtoRepository.countByMarcaIdAndAtivoTrue(marcaId) >= limits.maxProductsPerBrand()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_PRODUTOS_ATINGIDO);
        }

        produto.setNome(nomeNormalizado);
        produto.setCodigo(normalizarCodigo(codigo));
        produto.setCor(normalizarCor(cor));
        if (ativo != null) {
            produto.setAtivo(ativo);
        }
        return produtoRepository.save(produto);
    }

    @Transactional
    public Optional<Produto> excluir(Usuario gestor, UUID marcaId, UUID id) {
        marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId()).orElseThrow(ProdutoService::naoEncontrado);
        Produto produto = buscarProduto(marcaId, id);

        if (itemReposicaoRepository.existsByProdutoId(id)) {
            produto.setAtivo(false);
            return Optional.of(produtoRepository.save(produto));
        }

        produtoRepository.delete(produto);
        return Optional.empty();
    }

    private Marca buscarMarca(Usuario gestor, UUID marcaId) {
        return marcaRepository.findByIdAndGestorId(marcaId, gestor.getId()).orElseThrow(ProdutoService::naoEncontrado);
    }

    private Produto buscarProduto(UUID marcaId, UUID id) {
        return produtoRepository.findByIdAndMarcaId(id, marcaId).orElseThrow(ProdutoService::naoEncontrado);
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null || codigo.isBlank() ? null : codigo.trim();
    }

    private static String normalizarCor(String cor) {
        return cor == null || cor.isBlank() ? null : cor.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessRuleException naoEncontrado() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
