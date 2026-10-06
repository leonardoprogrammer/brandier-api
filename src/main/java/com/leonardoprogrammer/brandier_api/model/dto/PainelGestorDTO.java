package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;

public record PainelGestorDTO(PeriodoDTO periodo, List<MarcaNoPainelDTO> marcas, int limiteMarcas) {
}
