package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RequestRedefinirSenhaDTO {

    @NotBlank(message = "Informe o token de redefinição")
    @Size(max = 200, message = "Token inválido")
    private String token;

    @StrongPassword
    private String senha;

    public String getToken() {
        return token;
    }

    public String getSenha() {
        return senha;
    }
}
