package com.mds.inventoryservice.repository;

import com.mds.inventoryservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events for the current transaction. Rows already locked by another
     * relay instance are skipped, so instances never publish the same event concurrently.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit);

    long countByPublishedAtIsNull();

    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null")
    Instant findOldestUnpublishedCreatedAt();
}
