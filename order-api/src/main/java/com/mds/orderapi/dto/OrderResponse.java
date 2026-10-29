package com.mds.orderapi.dto;

import com.mds.orderapi.model.Order;
import com.mds.orderapi.model.OrderStatus;

import java.time.Instant;

/**
 * Order as returned by the REST API.
 *
 * @param orderId      client-supplied order id
 * @param itemId       ordered item
 * @param quantity     ordered units
 * @param status       reservation status
 * @param statusReason why the reservation was rejected, if it was
 * @param createdAt    when the order was accepted
 * @param updatedAt    when the order last changed
 */
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
