package com.mds.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    public static final String REASON_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String REASON_UNKNOWN_ITEM = "UNKNOWN_ITEM";

    /**
     * One reservation per order; the primary key is the consumer's idempotency guard.
     */
    @Id
    @Column(length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String itemId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    private String reason;

    /**
     * Broker messageId of the OrderPlaced event that produced this reservation, for tracing.
     */
    private String messageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Null on new instances, so Spring Data persists (INSERT) instead of merging. A concurrent duplicate then
     * fails on the primary key rather than silently overwriting the first reservation.
     */
    @Version
    private Long version;
}
