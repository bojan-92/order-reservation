package com.mds.inventoryservice.event;

import java.time.Instant;

/**
 * Event published by order-api when an order is accepted (schema version {@value #SCHEMA_VERSION}).
 *
 * @param orderId    order id, used to de-duplicate redeliveries
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
    /** AMQP message type this listener accepts. */
    public static final String TYPE = "OrderPlaced";
    /** Payload schema version this listener understands. */
    public static final int SCHEMA_VERSION = 1;
}
