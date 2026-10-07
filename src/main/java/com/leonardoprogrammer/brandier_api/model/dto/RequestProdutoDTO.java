package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestProdutoDTO(
        @NotBlank(message = "Informe o nome do produto")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
        String nome,

        @Size(max = 40, message = "O código deve ter no máximo 40 caracteres")
        String codigo,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Informe uma cor hexadecimal válida, como #4F46E5")
        String cor,

        Boolean ativo) {
}
