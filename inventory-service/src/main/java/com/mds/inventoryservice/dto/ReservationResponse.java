package com.mds.inventoryservice.dto;

import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;

import java.time.Instant;

/**
 * Reservation as returned by the REST API.
 *
 * @param orderId   order the reservation belongs to
 * @param itemId    requested item
 * @param quantity  requested units
 * @param status    whether stock was reserved
 * @param reason    why the reservation was rejected, if it was
 * @param createdAt when the order was processed
 */
public record ReservationResponse(
        String orderId,
        String itemId,
        int quantity,
        ReservationStatus status,
        String reason,
        Instant createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getOrderId(),
                reservation.getItemId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getReason(),
                reservation.getCreatedAt());
    }
}
