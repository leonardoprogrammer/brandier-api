package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;

public record ProdutosDaMarcaDTO(List<ProdutoDTO> produtos, int limite) {
}
