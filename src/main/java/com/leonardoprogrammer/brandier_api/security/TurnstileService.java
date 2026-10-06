package com.leonardoprogrammer.brandier_api.security;

import com.leonardoprogrammer.brandier_api.config.TurnstileProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);

    private final TurnstileProperties properties;
    private final RestClient restClient;

    public TurnstileService(TurnstileProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        if (properties.enabled() && properties.secretKey().isBlank()) {
            throw new IllegalStateException("brandier.turnstile.enabled=true exige brandier.turnstile.secret-key");
        }
    }

    public void verify(String token, String ip) {
        if (!properties.enabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw invalid();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", token);
        if (ip != null && !ip.isBlank()) {
            form.add("remoteip", ip);
        }

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(properties.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("Falha ao validar o Turnstile: {}", e.getClass().getSimpleName());
            throw invalid();
        }

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(ErrorCode.CAPTCHA_INVALIDO);
    }
}
