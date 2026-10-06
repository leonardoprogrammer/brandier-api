package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;

import java.time.Instant;
import java.util.UUID;

public record ConvitePendenteDTO(UUID id, String marcaNome, String gestorNome, Instant expiraEm, Instant criadoEm) {

    public static ConvitePendenteDTO de(ConvitePromotor convite) {
        return new ConvitePendenteDTO(convite.getId(), convite.getMarca().getNome(),
                convite.getMarca().getGestor().getNome(), convite.getExpiraEm(), convite.getCriadoEm());
    }
}
