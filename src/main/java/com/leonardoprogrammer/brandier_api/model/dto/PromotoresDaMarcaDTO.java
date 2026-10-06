package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.service.PromotorService.PromotoresDaMarca;

import java.util.List;

public record PromotoresDaMarcaDTO(List<PromotorDTO> promotores, List<ConviteDTO> convites, int usadas, int limite) {

    public static PromotoresDaMarcaDTO de(PromotoresDaMarca dados) {
        return new PromotoresDaMarcaDTO(
                dados.promotores().stream().map(PromotorDTO::de).toList(),
                dados.convites().stream().map(convite -> ConviteDTO.de(convite, dados.agora())).toList(),
                dados.usadas(),
                dados.limite());
    }
}
