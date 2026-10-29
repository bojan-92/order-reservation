package com.mds.orderapi.config;

import com.mds.orderapi.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbox health metrics, exposed through Actuator (e.g. {@code /actuator/prometheus}).
 */
@Configuration
public class MetricsConfig {

    /**
     * Registers gauges for the outbox backlog: {@code outbox.pending} (events waiting to be published) and
     * {@code outbox.oldest.age} (how long the oldest one has waited, the main signal that publishing is stuck).
     *
     * @param outboxRepository repository queried on each metrics scrape
     * @return binder that registers the gauges
     */
    @Bean
    public MeterBinder outboxMetrics(OutboxRepository outboxRepository) {
        return registry -> {
            Gauge.builder("outbox.pending", outboxRepository, OutboxRepository::countByPublishedAtIsNull)
                    .description("Outbox events not yet published")
                    .register(registry);

            Gauge.builder("outbox.oldest.age", outboxRepository, MetricsConfig::oldestAgeSeconds)
                    .description("Age of the oldest unpublished outbox event (seconds)")
                    .baseUnit("seconds")
                    .register(registry);
        };
    }

    /**
     * Age of the oldest unpublished outbox event.
     *
     * @param repo outbox repository
     * @return age in seconds, or 0 when nothing is pending
     */
    private static double oldestAgeSeconds(OutboxRepository repo) {
        Instant oldest = repo.findOldestUnpublishedCreatedAt();
        return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toSeconds();
    }
}