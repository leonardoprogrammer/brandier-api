package com.leonardoprogrammer.brandier_api.event;

public record PasswordResetEmailRequested(String to, String nome, String token) {
}
