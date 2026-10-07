package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.validation.StrongPassword;
import jakarta.validation.constraints.Size;

public record RequestAlterarSenhaDTO(
        @Size(max = 200, message = "Senha inválida")
        String senhaAtual,
        @StrongPassword
        String novaSenha) {
}
