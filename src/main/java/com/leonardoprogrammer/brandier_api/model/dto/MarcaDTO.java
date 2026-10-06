package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Marca;

import java.time.Instant;
import java.util.UUID;

public record MarcaDTO(UUID id, String nome, String descricao, Instant criadoEm) {

    public static MarcaDTO de(Marca marca) {
        return new MarcaDTO(marca.getId(), marca.getNome(), marca.getDescricao(), marca.getCriadoEm());
    }
}
