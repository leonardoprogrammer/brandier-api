package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestAtualizarNomeDTO(
        @NotBlank(message = "Informe seu nome")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome) {
}
