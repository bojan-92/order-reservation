package com.mds.orderapi;

import com.mds.orderapi.messaging.OutboxRelay;
import com.mds.orderapi.model.OutboxEvent;
import com.mds.orderapi.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates two relay instances against real Postgres: while one holds its batch, the other must skip it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxLockingIntegrationTest {

    // Replaced so the scheduled relay doesn't claim rows behind the test's back.
    @MockitoBean
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void concurrentRelays_neverClaimTheSameEvents() throws Exception {
        List<UUID> all = IntStream.range(0, 4)
                .mapToObj(i -> outboxRepository.save(event("lock-" + i)).getId())
                .toList();

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        CompletableFuture<List<UUID>> first = CompletableFuture.supplyAsync(() -> transactions.execute(tx -> {
            List<UUID> claimed = ids(outboxRepository.lockNextBatch(2));
            firstClaimed.countDown();
            awaitQuietly(releaseFirst);
            return claimed;
        }));

        assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();
        List<UUID> second = transactions.execute(tx -> ids(outboxRepository.lockNextBatch(10)));
        releaseFirst.countDown();
        List<UUID> firstIds = first.get(10, TimeUnit.SECONDS);

        assertThat(firstIds).hasSize(2);
        assertThat(second).hasSize(2).doesNotContainAnyElementsOf(firstIds);

        List<UUID> union = new ArrayList<>(firstIds);
        union.addAll(second);
        assertThat(union).containsExactlyInAnyOrderElementsOf(all);
    }

    private static List<UUID> ids(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getId).toList();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static OutboxEvent event(String orderId) {
        return OutboxEvent.builder()
                .aggregateId(orderId)
                .eventType("OrderPlaced")
                .schemaVersion(1)
                .payload("{\"orderId\":\"" + orderId + "\"}")
                .build();
    }
}
