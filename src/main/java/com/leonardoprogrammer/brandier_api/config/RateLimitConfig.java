package com.leonardoprogrammer.brandier_api.config;

import com.leonardoprogrammer.brandier_api.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
