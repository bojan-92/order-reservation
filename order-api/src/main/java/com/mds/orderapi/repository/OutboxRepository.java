package com.mds.orderapi.repository;

import com.mds.orderapi.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox storage used by the relay and the outbox metrics.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events for the current transaction. Rows already locked by another
     * relay instance are skipped, so instances never publish the same event concurrently.
     *
     * @param limit maximum number of events to claim
     * @return locked events, oldest first
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit);

    /**
     * Counts the outbox backlog.
     *
     * @return number of events not yet published
     */
    long countByPublishedAtIsNull();

    /**
     * Finds how far back the backlog reaches.
     *
     * @return creation time of the oldest unpublished event, or {@code null} if none
     */
    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null")
    Instant findOldestUnpublishedCreatedAt();
}
