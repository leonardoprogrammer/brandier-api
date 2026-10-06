package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestConviteDTO(
        @NotBlank(message = "Informe o e-mail da pessoa convidada")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
        String email) {
}
