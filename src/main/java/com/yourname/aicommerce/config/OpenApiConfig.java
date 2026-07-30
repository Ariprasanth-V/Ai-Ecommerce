package com.yourname.aicommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata configuration.
 */
@Configuration
public class OpenApiConfig {

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
                                .url("https://opensource.org/licenses/MIT")));
    }
}
