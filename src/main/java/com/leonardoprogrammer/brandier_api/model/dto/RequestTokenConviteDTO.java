package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestTokenConviteDTO(
        @NotBlank(message = "Informe o token do convite")
        @Size(max = 200, message = "Token inválido")
        String token) {
}
