package com.mds.inventoryservice.event;

import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;

import java.time.Instant;

/**
 * Event published to order-api once per order with the reservation outcome
 * (schema version {@value #SCHEMA_VERSION}).
 *
 * @param orderId    order the result belongs to
 * @param status     whether stock was reserved
 * @param reason     rejection reason, if rejected
 * @param occurredAt when this service decided
 */
public record ReservationResultEvent(
        String orderId,
        ReservationStatus status,
        String reason,
        Instant occurredAt
) {
    /** AMQP message type of this event. */
    public static final String TYPE = "InventoryReservationResult";
    /** Current payload schema version, sent in the {@code schemaVersion} header. */
    public static final int SCHEMA_VERSION = 1;

    public static ReservationResultEvent from(Reservation reservation) {
        return new ReservationResultEvent(reservation.getOrderId(), reservation.getStatus(),
                reservation.getReason(), reservation.getCreatedAt());
    }
}
