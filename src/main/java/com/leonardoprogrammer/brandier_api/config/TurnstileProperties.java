package com.leonardoprogrammer.brandier_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "brandier.turnstile")
public record TurnstileProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String secretKey,
        @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") String verifyUrl,
        @DefaultValue("5") int timeoutSeconds) {
}
