package com.mds.orderapi.event;

import com.mds.orderapi.model.Order;

import java.time.Instant;

public record OrderPlacedEvent(
        String orderId,
        String itemId,
        int quantity,
        Instant occurredAt
) {
    public static final String TYPE = "OrderPlaced";
    public static final int SCHEMA_VERSION = 1;

    public static OrderPlacedEvent from(Order order) {
        return new OrderPlacedEvent(order.getOrderId(), order.getItemId(), order.getQuantity(), order.getCreatedAt());
    }
}