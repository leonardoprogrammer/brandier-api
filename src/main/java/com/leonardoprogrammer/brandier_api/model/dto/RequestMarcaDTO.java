package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestMarcaDTO(
        @NotBlank(message = "Informe o nome da marca")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
        String nome,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String descricao) {
}
