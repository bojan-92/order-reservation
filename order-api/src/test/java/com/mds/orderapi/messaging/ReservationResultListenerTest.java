package com.mds.orderapi.messaging;

import com.mds.orderapi.event.ReservationResultEvent;
import com.mds.orderapi.exception.InvalidEventException;
import com.mds.orderapi.service.OrderService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationResultListenerTest {

    private static final String VALID_BODY = """
            {"orderId":"o-1","status":"REJECTED","reason":"insufficient stock","occurredAt":"2026-10-28T10:00:00Z"}
            """;

    @Mock
    private OrderService orderService;

    private ReservationResultListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReservationResultListener(JsonMapper.builder().build(), orderService);
    }

    @Test
    void validMessage_isAppliedToOrder() {
        listener.onReservationResult(message(ReservationResultEvent.TYPE, 1, VALID_BODY));

        ArgumentCaptor<ReservationResultEvent> event = ArgumentCaptor.forClass(ReservationResultEvent.class);
        verify(orderService).applyReservationResult(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo("o-1");
        assertThat(event.getValue().status()).isEqualTo(ReservationResultEvent.Status.REJECTED);
        assertThat(event.getValue().reason()).isEqualTo("insufficient stock");
    }

    @Test
    void unexpectedType_isRejected() {
        assertInvalid(message("SomethingElse", 1, VALID_BODY));
    }

    @Test
    void unsupportedSchemaVersion_isRejected() {
        assertInvalid(message(ReservationResultEvent.TYPE, 2, VALID_BODY));
    }

    @Test
    void missingSchemaVersion_isRejected() {
        assertInvalid(message(ReservationResultEvent.TYPE, null, VALID_BODY));
    }

    @Test
    void malformedJson_isRejected() {
        assertInvalid(message(ReservationResultEvent.TYPE, 1, "{not json"));
    }

    @Test
    void unknownStatus_isRejected() {
        assertInvalid(message(ReservationResultEvent.TYPE, 1, """
                {"orderId":"o-1","status":"MAYBE"}
                """));
    }

    @Test
    void missingRequiredFields_isRejected() {
        assertInvalid(message(ReservationResultEvent.TYPE, 1, """
                {"orderId":"o-1"}
                """));
    }

    private void assertInvalid(Message message) {
        assertThatThrownBy(() -> listener.onReservationResult(message))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(orderService);
    }

    private static Message message(String type, Integer schemaVersion, String body) {
        var builder = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setType(type)
                .setMessageId("m-1");
        if (schemaVersion != null) {
            builder.setHeader("schemaVersion", schemaVersion);
        }
        return builder.build();
    }
}
