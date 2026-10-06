package com.leonardoprogrammer.brandier_api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RequestVerificarEmailDTO {

    @NotBlank(message = "Informe o token de verificação")
    @Size(max = 200, message = "Token inválido")
    private String token;

    public String getToken() {
        return token;
    }
}
