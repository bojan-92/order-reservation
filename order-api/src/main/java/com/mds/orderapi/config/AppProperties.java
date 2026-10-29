package com.mds.orderapi.config;

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
     * @param exchange                    exchange that OrderPlaced events are published to
     * @param orderPlacedRoutingKey       routing key for OrderPlaced events
     * @param inventoryExchange           exchange the inventory service publishes reservation results to
     * @param reservationResultRoutingKey routing key for reservation results
     * @param reservationResultsQueue     queue this service consumes reservation results from
     * @param deadLetterExchange          exchange that rejected reservation results are dead-lettered to
     * @param reservationResultsDlq       queue holding dead-lettered reservation results
     */
    public record Messaging(
            String exchange,
            String orderPlacedRoutingKey,
            String inventoryExchange,
            String reservationResultRoutingKey,
            String reservationResultsQueue,
            String deadLetterExchange,
            String reservationResultsDlq) {
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