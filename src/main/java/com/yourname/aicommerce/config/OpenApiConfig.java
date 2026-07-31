package com.yourname.aicommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata and JWT security scheme configuration.
 * <p>
 * The {@code bearerAuth} scheme adds an "Authorize" button to the Swagger UI
 * where developers can paste a Bearer token to authenticate requests.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI aiCommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Commerce API")
                        .description("REST API for the AI-powered e-commerce platform. "
                                + "Built with Spring Boot 3.x and Java 21.")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("AI-Commerce Team")
                                .email("team@ai-commerce.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                // Apply Bearer auth globally — individual public endpoints show the
                // lock icon but are still accessible without a token.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your access token here. Obtain one via POST /api/auth/login.")));
    }
}
