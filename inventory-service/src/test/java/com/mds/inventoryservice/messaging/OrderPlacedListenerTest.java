package com.mds.inventoryservice.messaging;

import com.mds.inventoryservice.event.OrderPlacedEvent;
import com.mds.inventoryservice.exception.InvalidEventException;
import com.mds.inventoryservice.service.ReservationService;
import com.mds.inventoryservice.util.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderPlacedListenerTest {

    private static final String VALID_BODY = """
            {"orderId":"o-1","itemId":"item-1","quantity":2,"occurredAt":"2026-10-28T10:00:00Z"}
            """;

    @Mock
    private ReservationService reservationService;

    private OrderPlacedListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPlacedListener(JsonMapper.builder().build(), reservationService);
    }

    @Test
    void validMessage_isReservedWithMessageIdAndCorrelationId() {
        AtomicReference<String> correlationSeen = new AtomicReference<>();
        doAnswer(inv -> {
            correlationSeen.set(CorrelationId.current());
            return null;
        }).when(reservationService).reserve(any(), eq("m-1"));

        listener.onOrderPlaced(message(OrderPlacedEvent.TYPE, 1, VALID_BODY));

        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(reservationService).reserve(event.capture(), eq("m-1"));
        assertThat(event.getValue().orderId()).isEqualTo("o-1");
        assertThat(event.getValue().itemId()).isEqualTo("item-1");
        assertThat(event.getValue().quantity()).isEqualTo(2);
        assertThat(correlationSeen.get()).isEqualTo("corr-1");
    }

    @Test
    void unknownFields_areIgnored() {
        listener.onOrderPlaced(message(OrderPlacedEvent.TYPE, 1, """
                {"orderId":"o-1","itemId":"item-1","quantity":2,"customerId":"c-9"}
                """));

        verify(reservationService).reserve(any(), eq("m-1"));
    }

    @Test
    void unexpectedType_isRejected() {
        assertInvalid(message("SomethingElse", 1, VALID_BODY));
    }

    @Test
    void unsupportedSchemaVersion_isRejected() {
        assertInvalid(message(OrderPlacedEvent.TYPE, 2, VALID_BODY));
    }

    @Test
    void missingSchemaVersion_isRejected() {
        assertInvalid(message(OrderPlacedEvent.TYPE, null, VALID_BODY));
    }

    @Test
    void malformedJson_isRejected() {
        assertInvalid(message(OrderPlacedEvent.TYPE, 1, "{not json"));
    }

    @Test
    void missingItemId_isRejected() {
        assertInvalid(message(OrderPlacedEvent.TYPE, 1, """
                {"orderId":"o-1","quantity":2}
                """));
    }

    @Test
    void nonPositiveQuantity_isRejected() {
        assertInvalid(message(OrderPlacedEvent.TYPE, 1, """
                {"orderId":"o-1","itemId":"item-1","quantity":0}
                """));
    }

    private void assertInvalid(Message message) {
        assertThatThrownBy(() -> listener.onOrderPlaced(message))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(reservationService);
    }

    private static Message message(String type, Integer schemaVersion, String body) {
        var builder = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setType(type)
                .setMessageId("m-1")
                .setCorrelationId("corr-1");
        if (schemaVersion != null) {
            builder.setHeader("schemaVersion", schemaVersion);
        }
        return builder.build();
    }
}
