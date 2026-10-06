package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestReposicaoDTO(
        @NotNull(message = "Informe a loja")
        UUID lojaId,

        Instant repostoEm,

        @NotEmpty(message = "Informe ao menos um produto")
        @Size(max = 50, message = "Informe no máximo 50 produtos")
        List<@Valid @NotNull(message = "Item inválido") RequestItemReposicaoDTO> itens,

        @Size(max = 1000, message = "As observações devem ter no máximo 1000 caracteres")
        String observacoes) {
}
