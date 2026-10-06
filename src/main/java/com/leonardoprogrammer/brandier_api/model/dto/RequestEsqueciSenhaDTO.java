package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RequestEsqueciSenhaDTO {

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "Informe um e-mail válido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;

    public String getEmail() {
        return email;
    }
}
