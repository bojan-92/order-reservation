package com.mds.inventoryservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at {@code /v3/api-docs}, with Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    /**
     * API-level information shown at the top of Swagger UI.
     *
     * @return the OpenAPI definition
     */
    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Inventory Service")
                .version("1.0.0")
                .description("""
                        Read-only view of stock levels and reservation results. Stock changes only through \
                        `OrderPlaced` events from the order API, never through this REST API."""));
    }
}
