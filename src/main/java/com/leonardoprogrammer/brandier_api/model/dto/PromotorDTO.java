package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Promotor;

import java.time.Instant;
import java.util.UUID;

public record PromotorDTO(UUID id, UUID usuarioId, String nome, String email, Instant entrouEm) {

    public static PromotorDTO de(Promotor promotor) {
        return new PromotorDTO(promotor.getId(), promotor.getUsuario().getId(), promotor.getUsuario().getNome(),
                promotor.getUsuario().getEmail(), promotor.getEntrouEm());
    }
}
