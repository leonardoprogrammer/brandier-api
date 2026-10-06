package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Promotor;

import java.time.Instant;
import java.util.UUID;

public record VinculoPromotorDTO(UUID marcaId, String marcaNome, String gestorNome, Instant entrouEm) {

    public static VinculoPromotorDTO de(Promotor promotor) {
        return new VinculoPromotorDTO(promotor.getMarca().getId(), promotor.getMarca().getNome(),
                promotor.getMarca().getGestor().getNome(), promotor.getEntrouEm());
    }
}
