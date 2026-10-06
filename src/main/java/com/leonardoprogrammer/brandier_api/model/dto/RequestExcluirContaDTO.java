package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestExcluirContaDTO(
        @NotBlank(message = "Informe sua senha para confirmar")
        @Size(max = 200, message = "Senha inválida")
        String senha) {
}
