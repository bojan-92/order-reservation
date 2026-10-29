package com.mds.orderapi.messaging;

import com.mds.orderapi.config.AppProperties;
import com.mds.orderapi.model.OutboxEvent;
import com.mds.orderapi.repository.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final AppProperties PROPS = new AppProperties(
            new AppProperties.Messaging("orders.exchange", "order.placed", "inventory.exchange",
                    "reservation.result", "results", "dlx", "results.dlq"),
            new AppProperties.Outbox(1000, 60_000, 100, 1000));

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, rabbitTemplate, TransactionOperations.withoutTransaction(), PROPS, meterRegistry);
    }

    @Test
    void confirmedEvents_areMarkedPublishedWithMessageMetadata() {
        OutboxEvent first = event("o-1");
        OutboxEvent second = event("o-2");
        givenPending(first, second);
        brokerConfirms(cd -> cd.getFuture().complete(new CorrelationData.Confirm(true, null)));

        relay.publishPending();

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
        assertThat(meterRegistry.counter("outbox.published").count()).isEqualTo(2);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(2)).send(eq("orders.exchange"), eq("order.placed"), sent.capture(), any(CorrelationData.class));
        var props = sent.getAllValues().getFirst().getMessageProperties();
        assertThat(props.getMessageId()).isEqualTo(first.getId().toString());
        assertThat(props.getType()).isEqualTo("OrderPlaced");
        assertThat((Integer) props.getHeader("schemaVersion")).isEqualTo(1);
        assertThat(props.getCorrelationId()).isEqualTo("corr-o-1");
        assertThat(props.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void brokerNack_marksFailedAndDoesNotMarkPublished() {
        OutboxEvent event = event("o-1");
        givenPending(event);
        brokerConfirms(cd -> cd.getFuture().complete(new CorrelationData.Confirm(false, "internal error")));

        relay.publishPending();

        assertFailedOnce(event);
    }

    @Test
    void unroutableMessage_isTreatedAsFailure() {
        OutboxEvent event = event("o-1");
        givenPending(event);
        brokerConfirms(cd -> {
            cd.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "orders.exchange", "order.placed"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        relay.publishPending();

        assertFailedOnce(event);
    }

    @Test
    void failure_stopsBatchToPreserveOrderAndBacksOff() {
        OutboxEvent first = event("o-1");
        OutboxEvent second = event("o-2");
        givenPending(first, second);
        doThrow(new AmqpConnectException(new ConnectException("connection refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishPending();

        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertFailedOnce(first);
        assertThat(second.getAttempts()).isZero();
        assertThat(second.getPublishedAt()).isNull();
        assertThat(meterRegistry.counter("outbox.publish.failures").count()).isEqualTo(1);

        // The next tick falls inside the backoff window, so the outbox is not even polled.
        relay.publishPending();

        verify(outboxRepository, times(1)).lockNextBatch(anyInt());
    }

    private void givenPending(OutboxEvent... events) {
        when(outboxRepository.lockNextBatch(anyInt())).thenReturn(List.of(events));
    }

    private static void assertFailedOnce(OutboxEvent event) {
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotBlank();
    }

    private void brokerConfirms(Consumer<CorrelationData> confirm) {
        doAnswer(invocation -> {
            confirm.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static OutboxEvent event(String orderId) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(orderId)
                .eventType("OrderPlaced")
                .schemaVersion(1)
                .payload("{\"orderId\":\"" + orderId + "\"}")
                .correlationId("corr-" + orderId)
                .createdAt(Instant.now())
                .build();
    }
}
