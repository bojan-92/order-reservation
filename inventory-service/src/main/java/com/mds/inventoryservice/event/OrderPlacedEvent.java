package com.mds.inventoryservice.event;

import java.time.Instant;

public record OrderPlacedEvent(
        String orderId,
        String itemId,
        int quantity,
        Instant occurredAt
) {
    public static final String TYPE = "OrderPlaced";
    public static final int SCHEMA_VERSION = 1;
}
