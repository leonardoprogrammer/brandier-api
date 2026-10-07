package com.leonardoprogrammer.brandier_api.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "produto")
@BatchSize(size = 50)
public class Produto {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 40)
    private String codigo;

    @Column(length = 7)
    private String cor;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column
    private Instant atualizadoEm;

    public Produto() {
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Marca getMarca() {
        return marca;
    }

    public void setMarca(Marca marca) {
        this.marca = marca;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(Instant atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
