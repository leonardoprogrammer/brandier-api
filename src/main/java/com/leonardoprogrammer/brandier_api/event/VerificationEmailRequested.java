package com.leonardoprogrammer.brandier_api.event;

public record VerificationEmailRequested(String to, String nome, String token) {
}
