package com.mds.orderapi.dto;

import jakarta.validation.constraints.*;

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
