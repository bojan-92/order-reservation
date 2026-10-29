package com.mds.inventoryservice.messaging;

import com.mds.inventoryservice.event.OrderPlacedEvent;
import com.mds.inventoryservice.exception.InvalidEventException;
import com.mds.inventoryservice.service.ReservationService;
import com.mds.inventoryservice.util.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates incoming OrderPlaced messages and hands them to the reservation logic.
 * <p>
 * Failure handling: {@link InvalidEventException} is dead-lettered immediately; anything else (DB down,
 * concurrent duplicate) is retried with backoff and dead-lettered once retries are exhausted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedListener {

    private static final int MAX_ID_LENGTH = 64;

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    @RabbitListener(queues = "${app.messaging.order-placed-queue}")
    public void onOrderPlaced(Message message) {
        MessageProperties props = message.getMessageProperties();
        try (var ignored = CorrelationId.bind(props.getCorrelationId())) {
            OrderPlacedEvent event = parse(message);
            log.info("Received OrderPlaced for order {} (messageId={}, redelivered={})",
                    event.orderId(), props.getMessageId(), props.isRedelivered());

            reservationService.reserve(event, props.getMessageId());
        }
    }

    private OrderPlacedEvent parse(Message message) {
        MessageProperties props = message.getMessageProperties();

        if (!OrderPlacedEvent.TYPE.equals(props.getType())) {
            throw new InvalidEventException("Unexpected message type: " + props.getType());
        }

        Object version = props.getHeader("schemaVersion");
        if (version == null || !String.valueOf(OrderPlacedEvent.SCHEMA_VERSION).equals(version.toString())) {
            throw new InvalidEventException("Unsupported schemaVersion: " + version);
        }

        OrderPlacedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderPlacedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Malformed payload: " + e.getOriginalMessage(), e);
        }

        if (!validId(event.orderId()) || !validId(event.itemId())) {
            throw new InvalidEventException("Missing or invalid orderId/itemId");
        }
        if (event.quantity() <= 0) {
            throw new InvalidEventException("Quantity must be positive, got " + event.quantity());
        }
        return event;
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH;
    }
}
