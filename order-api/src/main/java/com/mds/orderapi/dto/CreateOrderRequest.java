package com.mds.orderapi.dto;

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
        @NotBlank
        @Size(max = 64)
        String orderId,

        @NotBlank
        @Size(max = 64)
        String itemId,

        @NotNull
        @Positive
        Integer quantity
) {
}
