package com.leonardoprogrammer.brandier_api.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleAuthConfig {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleAuthProperties properties) throws GeneralSecurityException, java.io.IOException {
        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance());
        if (!properties.clientId().isBlank()) {
            builder.setAudience(Collections.singletonList(properties.clientId()));
        }
        return builder.build();
    }
}
