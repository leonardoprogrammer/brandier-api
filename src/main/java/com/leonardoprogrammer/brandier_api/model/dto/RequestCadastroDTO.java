package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.validation.MaxBytes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RequestCadastroDTO {

    @NotBlank(message = "Informe seu nome")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    private String nome;

    @NotBlank(message = "Informe seu e-mail")
    @Email(message = "Informe um e-mail válido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;

    @NotBlank(message = "Informe uma senha")
    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "A senha deve conter letras e números")
    @MaxBytes(value = 72, message = "A senha é longa demais. Use menos caracteres acentuados ou especiais")
    private String senha;

    @AssertTrue(message = "É necessário aceitar os termos para criar a conta")
    private boolean aceitouTermos;

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenha() {
        return senha;
    }

    public boolean isAceitouTermos() {
        return aceitouTermos;
    }
}
