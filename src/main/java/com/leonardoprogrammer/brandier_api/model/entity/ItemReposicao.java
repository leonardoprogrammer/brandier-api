package com.leonardoprogrammer.brandier_api.model.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "item_reposicao")
public class ItemReposicao {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reposicao_id", nullable = false)
    private Reposicao reposicao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade;

    public ItemReposicao() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Reposicao getReposicao() {
        return reposicao;
    }

    public void setReposicao(Reposicao reposicao) {
        this.reposicao = reposicao;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }
}
