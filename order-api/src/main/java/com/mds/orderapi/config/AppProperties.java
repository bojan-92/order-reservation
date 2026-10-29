package com.mds.orderapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Messaging messaging, Outbox outbox) {

    public record Messaging(
            String exchange,
            String orderPlacedRoutingKey,
            String inventoryExchange,
            String reservationResultRoutingKey,
            String reservationResultsQueue,
            String deadLetterExchange,
            String reservationResultsDlq) {
    }

    public record Outbox(long relayIntervalMs, long maxBackoffMs, int batchSize, long confirmTimeoutMs) {
    }
}