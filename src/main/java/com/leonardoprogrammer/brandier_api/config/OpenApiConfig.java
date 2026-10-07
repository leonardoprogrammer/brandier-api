package com.leonardoprogrammer.brandier_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Brandier API")
                .description("""
                        API REST da plataforma de gestão de promotores de marca. Projeto de portfólio, sem fins comerciais.

                        **Autenticação**: a API usa cookies `HttpOnly` (JWT de acesso + token de atualização opaco), nunca cabeçalho `Authorization`. \
                        Para testar POSTs/PUTs/DELETEs por aqui, faça login em `/v1/usuario/login` no navegador, copie o valor do cookie `XSRF-TOKEN` \
                        e cole no cabeçalho `X-XSRF-TOKEN` desta página (CSRF é obrigatório em toda rota que muda estado).

                        **Autorização por recurso**: cada rota filtra pelo usuário autenticado (gestor da marca ou promotor ativo dela); \
                        um recurso de outro usuário responde 404, nunca 403, para não revelar que ele existe.

                        **Erros**: respostas de erro seguem RFC 7807 (`ProblemDetail`) com um campo `codigo` estável em português \
                        (ex.: `LIMITE_MARCAS_ATINGIDO`, `NAO_AUTENTICADO`), documentado em cada operação abaixo.
                        """)
                .version("v1")
                .contact(new Contact().name("Leonardo").url("https://leonardoprogrammer.com.br"))
                .license(new License().name("MIT").url("https://github.com/leonardoprogrammer/brandier-api/blob/main/LICENSE")));
    }

    @Bean
    public GroupedOpenApi groupV1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch(ApiVersion.V1 + "/**")
                .build();
    }
}
