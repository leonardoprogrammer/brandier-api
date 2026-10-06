package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Loja;

import java.time.Instant;
import java.util.UUID;

public record LojaDTO(UUID id, String nome, String cidade, String uf, String endereco, Instant criadoEm) {

    public static LojaDTO de(Loja loja) {
        return new LojaDTO(loja.getId(), loja.getNome(), loja.getCidade(), loja.getUf(), loja.getEndereco(),
                loja.getCriadoEm());
    }
}
