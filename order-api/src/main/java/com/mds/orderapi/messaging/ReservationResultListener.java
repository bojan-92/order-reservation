package com.mds.orderapi.messaging;

import com.mds.orderapi.event.ReservationResultEvent;
import com.mds.orderapi.exception.InvalidEventException;
import com.mds.orderapi.service.OrderService;
import com.mds.orderapi.util.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes reservation results from the inventory service and applies them to orders. Invalid messages throw
 * {@link InvalidEventException} and are dead-lettered without retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationResultListener {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    /**
     * Handles one reservation result, with the producer's correlation id bound for logging.
     *
     * @param message raw AMQP message
     * @throws InvalidEventException if the message can never be processed
     */
    @RabbitListener(queues = "${app.messaging.reservation-results-queue}")
    public void onReservationResult(Message message) {
        try (var ignored = CorrelationId.bind(message.getMessageProperties().getCorrelationId())) {
            ReservationResultEvent event = parse(message);
            log.info("Received {} for order {} (messageId={})",
                    event.status(), event.orderId(), message.getMessageProperties().getMessageId());

            orderService.applyReservationResult(event);
        }
    }

    /**
     * Checks the message type and schema version, then parses and validates the payload.
     *
     * @param message raw AMQP message
     * @return the parsed event
     * @throws InvalidEventException if any check fails
     */
    private ReservationResultEvent parse(Message message) {
        MessageProperties props = message.getMessageProperties();

        if (!ReservationResultEvent.TYPE.equals(props.getType())) {
            throw new InvalidEventException("Unexpected message type: " + props.getType());
        }

        Object version = props.getHeader("schemaVersion");
        if (version == null || !String.valueOf(ReservationResultEvent.SCHEMA_VERSION).equals(version.toString())) {
            throw new InvalidEventException("Unsupported schemaVersion: " + version);
        }

        ReservationResultEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), ReservationResultEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Malformed payload: " + e.getOriginalMessage(), e);
        }

        if (event.orderId() == null || event.status() == null) {
            throw new InvalidEventException("Missing orderId or status");
        }
        return event;
    }
}