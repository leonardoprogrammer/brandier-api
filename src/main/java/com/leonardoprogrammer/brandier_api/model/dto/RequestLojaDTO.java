package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestLojaDTO(
        @NotBlank(message = "Informe o nome da loja")
        @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
        String nome,

        @NotBlank(message = "Informe a cidade")
        @Size(max = 80, message = "A cidade deve ter no máximo 80 caracteres")
        String cidade,

        @NotBlank(message = "Informe a UF")
        @Pattern(regexp = "(?i)\\s*(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\s*",
                message = "Informe uma UF válida")
        String uf,

        @Size(max = 200, message = "O endereço deve ter no máximo 200 caracteres")
        String endereco) {
}
