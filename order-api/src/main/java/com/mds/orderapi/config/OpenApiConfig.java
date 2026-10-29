package com.mds.orderapi.config;

import com.mds.orderapi.util.CorrelationId;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
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
    public OpenAPI orderApiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order API")
                .version("1.0.0")
                .description("""
                        Accepts orders and publishes them to the inventory service. Stock is reserved \
                        asynchronously: `POST /orders` returns `202` with status `PENDING`, and \
                        `GET /orders/{orderId}` shows the final `RESERVED` or `REJECTED` status. \
                        Requests are idempotent on `orderId`."""));
    }

    /**
     * Documents the optional correlation id header on every operation. It is handled by
     * {@link com.mds.orderapi.filter.CorrelationIdFilter}, so it doesn't appear in controller signatures.
     *
     * @return customizer adding the header parameter
     */
    @Bean
    public OperationCustomizer correlationIdHeader() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(CorrelationId.HTTP_HEADER)
                .required(false)
                .description("Traces the request through both services and their logs. "
                        + "Echoed in the response; generated if missing or invalid.")
                .schema(new StringSchema().pattern("[A-Za-z0-9._-]{1,64}").example("demo-1")));
    }
}
