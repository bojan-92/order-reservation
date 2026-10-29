package com.mds.inventoryservice.event;

import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;

import java.time.Instant;

public record ReservationResultEvent(
        String orderId,
        ReservationStatus status,
        String reason,
        Instant occurredAt
) {
    public static final String TYPE = "InventoryReservationResult";
    public static final int SCHEMA_VERSION = 1;

    public static ReservationResultEvent from(Reservation reservation) {
        return new ReservationResultEvent(reservation.getOrderId(), reservation.getStatus(),
                reservation.getReason(), reservation.getCreatedAt());
    }
}
