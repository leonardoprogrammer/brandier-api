package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;

public record LojasDaMarcaDTO(List<LojaDTO> lojas, int limite) {
}
