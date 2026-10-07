package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Produto;

import java.time.Instant;
import java.util.UUID;

public record ProdutoDTO(UUID id, String nome, String codigo, String cor, boolean ativo, Instant criadoEm) {

    public static ProdutoDTO de(Produto produto) {
        return new ProdutoDTO(produto.getId(), produto.getNome(), produto.getCodigo(), produto.getCor(),
                produto.isAtivo(), produto.getCriadoEm());
    }
}
