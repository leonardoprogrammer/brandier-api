package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;

import java.time.Instant;
import java.util.UUID;

public record ConviteDTO(UUID id, String email, StatusConvite status, Instant expiraEm, Instant criadoEm) {

    public static ConviteDTO de(ConvitePromotor convite, Instant agora) {
        return new ConviteDTO(convite.getId(), convite.getEmail(), convite.statusEm(agora), convite.getExpiraEm(),
                convite.getCriadoEm());
    }
}
