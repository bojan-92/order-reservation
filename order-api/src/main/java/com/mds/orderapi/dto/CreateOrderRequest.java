package com.mds.orderapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * Payload of {@code POST /orders}. Ids are opaque client strings; their length is limited only to fit the
 * database columns.
 *
 * @param orderId  client-supplied order id, also the idempotency key
 * @param itemId   item to reserve
 * @param quantity number of units to reserve, must be positive
 */
public record CreateOrderRequest(
        @Schema(description = "Client-supplied order id, also the idempotency key", example = "ord-1")
        @NotBlank
        @Size(max = 64)
        String orderId,

        @Schema(description = "Item to reserve", example = "item-1")
        @NotBlank
        @Size(max = 64)
        String itemId,

        @Schema(description = "Units to reserve", example = "2")
        @NotNull
        @Positive
        Integer quantity
) {
}
