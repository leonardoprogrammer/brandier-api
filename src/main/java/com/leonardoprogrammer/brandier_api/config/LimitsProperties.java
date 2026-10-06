package com.leonardoprogrammer.brandier_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "brandier.limits")
public record LimitsProperties(
        @DefaultValue("2") int maxBrandsPerManager,
        @DefaultValue("5") int maxStoresPerBrand,
        @DefaultValue("10") int maxProductsPerBrand,
        @DefaultValue("5") int maxPromotersPerBrand) {
}
