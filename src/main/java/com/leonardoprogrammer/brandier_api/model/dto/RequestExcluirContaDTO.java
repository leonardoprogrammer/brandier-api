package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.Size;

public record RequestExcluirContaDTO(
        @Size(max = 200, message = "Senha inválida")
        String senha) {
}
