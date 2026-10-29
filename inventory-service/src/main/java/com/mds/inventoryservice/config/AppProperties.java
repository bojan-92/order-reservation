package com.mds.inventoryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from the properties.
 *
 * @param messaging broker exchange, queue and routing key names
 * @param outbox    outbox relay tuning
 */
@ConfigurationProperties("app")
public record AppProperties(Messaging messaging, Outbox outbox) {

    /**
     * RabbitMQ topology names.
     *
     * @param ordersExchange              exchange order-api publishes OrderPlaced events to
     * @param orderPlacedRoutingKey       routing key for OrderPlaced events
     * @param orderPlacedQueue            queue this service consumes OrderPlaced events from
     * @param deadLetterExchange          exchange that rejected OrderPlaced events are dead-lettered to
     * @param orderPlacedDlq              queue holding dead-lettered OrderPlaced events
     * @param inventoryExchange           exchange that reservation results are published to
     * @param reservationResultRoutingKey routing key for reservation results
     */
    public record Messaging(
            String ordersExchange,
            String orderPlacedRoutingKey,
            String orderPlacedQueue,
            String deadLetterExchange,
            String orderPlacedDlq,
            String inventoryExchange,
            String reservationResultRoutingKey) {
    }

    /**
     * Outbox relay tuning.
     *
     * @param relayIntervalMs  delay between relay runs, also the first retry delay after a failure
     * @param maxBackoffMs     upper bound for the exponential retry delay after consecutive failures
     * @param batchSize        maximum number of events claimed per relay run
     * @param confirmTimeoutMs how long to wait for a broker publisher confirm per event
     */
    public record Outbox(long relayIntervalMs, long maxBackoffMs, int batchSize, long confirmTimeoutMs) {
    }
}
