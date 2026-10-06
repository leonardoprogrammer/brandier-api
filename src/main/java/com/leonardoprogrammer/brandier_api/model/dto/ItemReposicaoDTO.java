package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;

import java.util.UUID;

public record ItemReposicaoDTO(UUID produtoId, String produtoNome, int quantidade) {

    public static ItemReposicaoDTO de(ItemReposicao item) {
        return new ItemReposicaoDTO(item.getProduto().getId(), item.getProduto().getNome(), item.getQuantidade());
    }
}
