package com.mds.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Messaging messaging, Outbox outbox) {

    public record Messaging(
            String ordersExchange,
            String orderPlacedRoutingKey,
            String orderPlacedQueue,
            String deadLetterExchange,
            String orderPlacedDlq,
            String inventoryExchange,
            String reservationResultRoutingKey) {
    }

    public record Outbox(long relayIntervalMs, long maxBackoffMs, int batchSize, long confirmTimeoutMs) {
    }
}
