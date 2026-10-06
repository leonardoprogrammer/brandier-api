package com.leonardoprogrammer.brandier_api.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Limite de tentativas excedido");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
