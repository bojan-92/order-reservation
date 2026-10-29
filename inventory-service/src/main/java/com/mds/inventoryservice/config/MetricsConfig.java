package com.mds.inventoryservice.config;

import com.mds.inventoryservice.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class MetricsConfig {

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

    private static double oldestAgeSeconds(OutboxRepository repo) {
        Instant oldest = repo.findOldestUnpublishedCreatedAt();
        return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toSeconds();
    }
}
