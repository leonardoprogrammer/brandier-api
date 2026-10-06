package com.leonardoprogrammer.brandier_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Brandier API")
                .description("API da plataforma de gestão de promotores de marca.")
                .version("v1"));
    }

    @Bean
    public GroupedOpenApi groupV1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch(ApiVersion.V1 + "/**")
                .build();
    }
}
