package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestGoogleLoginDTO(
        @NotBlank(message = "Informe o token do Google")
        @Size(max = 4096, message = "Token inválido")
        String credential) {
}
