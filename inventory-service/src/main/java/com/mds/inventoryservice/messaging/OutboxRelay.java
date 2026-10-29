package com.mds.inventoryservice.messaging;

import com.mds.inventoryservice.config.AppProperties;
import com.mds.inventoryservice.model.OutboxEvent;
import com.mds.inventoryservice.repository.OutboxRepository;
import com.mds.inventoryservice.util.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes events in creation order. An event is marked published only after
 * the broker confirms it and it was routed to a queue, so delivery is at-least-once; consumers dedupe
 * by messageId / orderId.
 * <p>
 * Each batch is claimed with {@code FOR UPDATE SKIP LOCKED} inside one transaction, so several instances
 * can run the relay side by side without publishing the same event twice.
 */
@Slf4j
@Component
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionOperations transactions;
    private final AppProperties.Messaging messaging;
    private final AppProperties.Outbox outbox;
    private final Counter publishedCounter;
    private final Counter failureCounter;
    private int consecutiveFailures;
    private Instant retryAt = Instant.EPOCH;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, TransactionOperations transactions,
                       AppProperties appProperties, MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactions = transactions;
        this.messaging = appProperties.messaging();
        this.outbox = appProperties.outbox();
        this.publishedCounter = meterRegistry.counter("outbox.published");
        this.failureCounter = meterRegistry.counter("outbox.publish.failures");
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms}")
    public void publishPending() {
        if (Instant.now().isBefore(retryAt)) {
            return;
        }
        transactions.executeWithoutResult(tx -> {
            for (OutboxEvent event : outboxRepository.lockNextBatch(outbox.batchSize())) {
                if (!publish(event)) {
                    return;
                }
            }
        });
    }

    private boolean publish(OutboxEvent event) {
        try (var ignored = CorrelationId.bind(event.getCorrelationId())) {
            try {
                send(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                onFailure(event, e);
                return false;
            }
            event.setPublishedAt(Instant.now());
            publishedCounter.increment();
            if (consecutiveFailures > 0) {
                log.info("Publishing recovered after {} failed attempts", consecutiveFailures);
                consecutiveFailures = 0;
            }
            log.info("Published {} for order {}", event.getEventType(), event.getAggregateId());
            return true;
        }
    }

    private void onFailure(OutboxEvent event, Exception e) {
        failureCounter.increment();
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(e.toString()));

        long backoffMs = Math.min(outbox.relayIntervalMs() << Math.min(consecutiveFailures, 20), outbox.maxBackoffMs());
        consecutiveFailures++;
        retryAt = Instant.now().plusMillis(backoffMs);
        log.warn("Publishing {} for order {} failed (attempt {}), retrying in {} ms: {}",
                event.getEventType(), event.getAggregateId(), event.getAttempts(), backoffMs, e.getMessage());
    }

    private void send(OutboxEvent event) throws Exception {
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        rabbitTemplate.send(messaging.inventoryExchange(), messaging.reservationResultRoutingKey(), toMessage(event), correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(outbox.confirmTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("Broker nack: " + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("No queue bound for routing key '" + messaging.reservationResultRoutingKey() + "'");
        }
    }

    private Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setCorrelationId(event.getCorrelationId())
                .setType(event.getEventType())
                .setHeader("schemaVersion", event.getSchemaVersion())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
