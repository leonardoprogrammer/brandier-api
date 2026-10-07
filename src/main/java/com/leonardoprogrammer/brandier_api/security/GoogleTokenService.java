package com.leonardoprogrammer.brandier_api.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.leonardoprogrammer.brandier_api.config.GoogleAuthProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenService.class);

    public record GooglePerfil(String email, String nome, boolean emailVerificado) {
    }

    private final GoogleAuthProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenService(GoogleAuthProperties properties, GoogleIdTokenVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
    }

    public GooglePerfil verificar(String credential) {
        if (properties.clientId().isBlank()) {
            throw new BusinessRuleException(ErrorCode.GOOGLE_AUTH_INDISPONIVEL);
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(credential);
        } catch (Exception e) {
            log.warn("Falha ao verificar o token do Google: {}", e.getClass().getSimpleName());
            throw new BusinessRuleException(ErrorCode.GOOGLE_TOKEN_INVALIDO);
        }

        if (idToken == null) {
            throw new BusinessRuleException(ErrorCode.GOOGLE_TOKEN_INVALIDO);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        return new GooglePerfil(payload.getEmail(), (String) payload.get("name"), Boolean.TRUE.equals(payload.getEmailVerified()));
    }
}
