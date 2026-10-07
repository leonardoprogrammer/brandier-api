package com.leonardoprogrammer.brandier_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "brandier.google")
public record GoogleAuthProperties(
        @DefaultValue("") String clientId) {
}
