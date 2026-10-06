package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;

import java.time.Instant;

public record ConviteRecebidoDTO(String marcaNome, String gestorNome, String email, Instant expiraEm) {

    public static ConviteRecebidoDTO de(ConvitePromotor convite) {
        return new ConviteRecebidoDTO(convite.getMarca().getNome(), convite.getMarca().getGestor().getNome(),
                convite.getEmail(), convite.getExpiraEm());
    }
}
