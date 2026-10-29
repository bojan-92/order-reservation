package com.mds.orderapi.event;

import com.mds.orderapi.model.Order;

import java.time.Instant;

/**
 * Event published to the inventory service when an order is accepted (schema version {@value #SCHEMA_VERSION}).
 *
 * @param orderId    order id, which consumers use to de-duplicate
 * @param itemId     item to reserve
 * @param quantity   units to reserve
 * @param occurredAt when the order was accepted
 */
public record OrderPlacedEvent(
        String orderId,
        String itemId,
        int quantity,
        Instant occurredAt
) {
    /** AMQP message type of this event. */
    public static final String TYPE = "OrderPlaced";
    /** Current payload schema version, sent in the {@code schemaVersion} header. */
    public static final int SCHEMA_VERSION = 1;

    public static OrderPlacedEvent from(Order order) {
        return new OrderPlacedEvent(order.getOrderId(), order.getItemId(), order.getQuantity(), order.getCreatedAt());
    }
}