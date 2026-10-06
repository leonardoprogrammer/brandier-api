package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;
import java.util.UUID;

public record PainelMarcaDTO(
        PeriodoDTO periodo,
        UUID marcaId,
        String marcaNome,
        TotaisDTO totais,
        List<AgregadoDTO> porLoja,
        List<AgregadoDTO> porPromotor,
        List<AgregadoDTO> porProduto,
        List<PromotorDTO> promotoresSemAtividade,
        UsoDeLimitesDTO uso) {
}
