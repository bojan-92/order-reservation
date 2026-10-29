package com.mds.orderapi.event;

import java.time.Instant;

public record ReservationResultEvent(
        String orderId,
        Status status,
        String reason,
        Instant occurredAt
) {
    public static final String TYPE = "InventoryReservationResult";
    public static final int SCHEMA_VERSION = 1;

    public enum Status {
        RESERVED,
        REJECTED
    }
}