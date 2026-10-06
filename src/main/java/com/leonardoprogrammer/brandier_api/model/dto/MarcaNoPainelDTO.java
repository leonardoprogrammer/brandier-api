package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.UUID;

public record MarcaNoPainelDTO(UUID marcaId, String marcaNome, TotaisDTO totais, UsoDeLimitesDTO uso) {
}
