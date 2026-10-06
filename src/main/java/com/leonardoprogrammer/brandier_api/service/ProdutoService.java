package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final MarcaRepository marcaRepository;
    private final LimitsProperties limits;

    public ProdutoService(ProdutoRepository produtoRepository,
                          MarcaRepository marcaRepository,
                          LimitsProperties limits) {
        this.produtoRepository = produtoRepository;
        this.marcaRepository = marcaRepository;
        this.limits = limits;
    }

    public int limiteDeProdutos() {
        return limits.maxProductsPerBrand();
    }

    @Transactional(readOnly = true)
    public List<Produto> listar(Usuario gestor, UUID marcaId) {
        buscarMarca(gestor, marcaId);
        return produtoRepository.findAllByMarcaIdOrderByNomeAsc(marcaId);
    }

    @Transactional(readOnly = true)
    public Produto buscar(Usuario gestor, UUID marcaId, UUID id) {
        buscarMarca(gestor, marcaId);
        return buscarProduto(marcaId, id);
    }

    @Transactional
    public Produto criar(Usuario gestor, UUID marcaId, String nome, String codigo) {
        Marca marca = marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId())
                .orElseThrow(ProdutoService::naoEncontrado);

        if (produtoRepository.countByMarcaId(marcaId) >= limits.maxProductsPerBrand()) {
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
        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto atualizar(Usuario gestor, UUID marcaId, UUID id, String nome, String codigo) {
        buscarMarca(gestor, marcaId);
        Produto produto = buscarProduto(marcaId, id);

        String nomeNormalizado = nome.trim();
        if (produtoRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marcaId, nomeNormalizado, id)) {
            throw new BusinessRuleException(ErrorCode.PRODUTO_NOME_DUPLICADO);
        }

        produto.setNome(nomeNormalizado);
        produto.setCodigo(normalizarCodigo(codigo));
        return produtoRepository.save(produto);
    }

    @Transactional
    public void excluir(Usuario gestor, UUID marcaId, UUID id) {
        buscarMarca(gestor, marcaId);
        produtoRepository.delete(buscarProduto(marcaId, id));
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

    private static BusinessRuleException naoEncontrado() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
