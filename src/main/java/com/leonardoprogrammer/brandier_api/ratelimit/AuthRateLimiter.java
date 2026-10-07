package com.leonardoprogrammer.brandier_api.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthRateLimiter {

    private final RateLimitProperties properties;
    private final FixedWindowRateLimiter limiter;

    public AuthRateLimiter(RateLimitProperties properties, FixedWindowRateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    public void checkLogin(String ip, String canonicalEmail) {
        Duration window = Duration.ofMinutes(properties.loginWindowMinutes());
        consume("login:ip:" + ip, properties.loginIp(), window);
        consume("login:email:" + canonicalEmail, properties.loginEmail(), window);
    }

    public void checkSignup(String ip) {
        consume("signup:ip:" + ip, properties.signupIp(), defaultWindow());
    }

    public void checkRecovery(String operation, String ip, String canonicalEmail) {
        consume(operation + ":ip:" + ip, properties.recoveryIp(), defaultWindow());
        consume(operation + ":email:" + canonicalEmail, properties.recoveryEmail(), defaultWindow());
    }

    public void checkAccountDeletion(String userKey) {
        consume("delete-account:user:" + userKey, properties.recoveryEmail(), defaultWindow());
    }

    public void checkPasswordChange(String userKey) {
        consume("change-password:user:" + userKey, properties.recoveryEmail(), defaultWindow());
    }

    public void checkToken(String operation, String ip) {
        consume(operation + ":ip:" + ip, properties.tokenIp(), defaultWindow());
    }

    public int purgeExpired() {
        return limiter.purgeExpired();
    }

    private Duration defaultWindow() {
        return Duration.ofMinutes(properties.windowMinutes());
    }

    private void consume(String key, int limit, Duration window) {
        if (!properties.enabled()) {
            return;
        }
        limiter.tryConsume(key, limit, window).ifPresent(retryAfter -> {
            throw new RateLimitExceededException(retryAfter);
        });
    }
}
