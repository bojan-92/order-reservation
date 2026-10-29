package com.mds.orderapi.dto;

import com.mds.orderapi.model.Order;
import com.mds.orderapi.model.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        String orderId,
        String itemId,
        int quantity,
        OrderStatus status,
        String statusReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getItemId(),
                order.getQuantity(),
                order.getStatus(),
                order.getStatusReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
