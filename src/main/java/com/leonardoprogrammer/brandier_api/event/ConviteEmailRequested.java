package com.leonardoprogrammer.brandier_api.event;

public record ConviteEmailRequested(String to, String gestorNome, String marcaNome, String token) {
}
