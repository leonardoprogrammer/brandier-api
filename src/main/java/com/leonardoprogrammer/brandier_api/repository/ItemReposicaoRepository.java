package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemReposicaoRepository extends JpaRepository<ItemReposicao, UUID> {

    boolean existsByProdutoId(UUID produtoId);
}
