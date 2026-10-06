package com.leonardoprogrammer.brandier_api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank(message = "Informe uma senha")
@Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "A senha deve conter letras e números")
@MaxBytes(value = 72, message = "A senha é longa demais. Use menos caracteres acentuados ou especiais")
public @interface StrongPassword {

    String message() default "Senha inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
