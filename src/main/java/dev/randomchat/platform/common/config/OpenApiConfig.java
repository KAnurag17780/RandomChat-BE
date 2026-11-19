package dev.randomchat.platform.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the springdoc-openapi documentation endpoint.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Random Chat Platform API")
                        .description("Pseudonymous 1-to-1 random text chat backend. " +
                                "Authenticated REST endpoints and a WebSocket chat protocol.")
                        .version("0.1.0")
                        .license(new License().name("MIT")));
    }
}
