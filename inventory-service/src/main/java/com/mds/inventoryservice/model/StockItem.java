package com.mds.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem {

    @Id
    @Column(length = 64)
    private String itemId;

    @Column(nullable = false)
    private int available;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
