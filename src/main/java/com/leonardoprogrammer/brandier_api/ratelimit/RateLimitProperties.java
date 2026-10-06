package com.leonardoprogrammer.brandier_api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "brandier.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int loginIp,
        @DefaultValue("10") int loginEmail,
        @DefaultValue("15") int loginWindowMinutes,
        @DefaultValue("5") int signupIp,
        @DefaultValue("10") int recoveryIp,
        @DefaultValue("3") int recoveryEmail,
        @DefaultValue("20") int tokenIp,
        @DefaultValue("60") int windowMinutes) {
}
