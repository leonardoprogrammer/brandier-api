package com.leonardoprogrammer.brandier_api.model.entity;

import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 254)
    private String emailCanonico;

    @Column(nullable = false, length = 100)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusUsuario status = StatusUsuario.PENDENTE;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column
    private Instant termosAceitosEm;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column
    private Instant atualizadoEm;

    public Usuario() {
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now();
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    public Instant getTermosAceitosEm() {
        return termosAceitosEm;
    }

    public void setTermosAceitosEm(Instant termosAceitosEm) {
        this.termosAceitosEm = termosAceitosEm;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailCanonico() {
        return emailCanonico;
    }

    public void setEmailCanonico(String emailCanonico) {
        this.emailCanonico = emailCanonico;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    public void setStatus(StatusUsuario status) {
        this.status = status;
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
