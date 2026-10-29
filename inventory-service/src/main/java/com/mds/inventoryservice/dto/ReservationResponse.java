package com.mds.inventoryservice.dto;

import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;

import java.time.Instant;

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
