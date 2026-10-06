package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestItemReposicaoDTO(
        @NotNull(message = "Informe o produto")
        UUID produtoId,

        @NotNull(message = "Informe a quantidade")
        @Min(value = 1, message = "A quantidade deve ser maior que zero")
        @Max(value = 1000000, message = "A quantidade deve ser no máximo 1.000.000")
        Integer quantidade) {
}
