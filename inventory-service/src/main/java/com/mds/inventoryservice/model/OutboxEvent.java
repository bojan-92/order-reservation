package com.mds.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Event waiting to be published, written in the same transaction as the business change (transactional outbox).
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String aggregateId;

    private String eventType;

    private int schemaVersion;

    private String payload;

    private String correlationId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private int attempts;

    private String lastError;
}
