package com.leonardoprogrammer.brandier_api.model.dto;

public class UsuarioDTO {

    private String id;
    private String nome;
    private String email;
    private boolean temSenha;
    private boolean termosAceitos;

    public UsuarioDTO(String id, String nome, String email, boolean temSenha, boolean termosAceitos) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.temSenha = temSenha;
        this.termosAceitos = termosAceitos;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public boolean isTemSenha() {
        return temSenha;
    }

    public void setTemSenha(boolean temSenha) {
        this.temSenha = temSenha;
    }

    public boolean isTermosAceitos() {
        return termosAceitos;
    }

    public void setTermosAceitos(boolean termosAceitos) {
        this.termosAceitos = termosAceitos;
    }
}
