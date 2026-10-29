package com.mds.orderapi.event;

import java.time.Instant;

/**
 * Reservation outcome published by the inventory service (schema version {@value #SCHEMA_VERSION}).
 *
 * @param orderId    order the result belongs to
 * @param status     whether stock was reserved
 * @param reason     rejection reason, if rejected
 * @param occurredAt when the inventory service decided
 */
public record ReservationResultEvent(
        String orderId,
        Status status,
        String reason,
        Instant occurredAt
) {
    /** AMQP message type this listener accepts. */
    public static final String TYPE = "InventoryReservationResult";
    /** Payload schema version this listener understands. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Reservation status outcome.
     */
    public enum Status {
        RESERVED,
        REJECTED
    }
}