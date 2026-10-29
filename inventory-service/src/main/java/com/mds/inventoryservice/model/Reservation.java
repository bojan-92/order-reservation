package com.mds.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * The outcome of processing one order: stock reserved, or rejected with a reason.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    /** Rejection reason: the item exists but has fewer units than requested. */
    public static final String REASON_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    /** Rejection reason: no stock item exists with the requested id. */
    public static final String REASON_UNKNOWN_ITEM = "UNKNOWN_ITEM";

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

    private String messageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;
}
