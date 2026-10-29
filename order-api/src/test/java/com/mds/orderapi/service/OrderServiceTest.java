package com.mds.orderapi.service;

import com.mds.orderapi.dto.CreateOrderRequest;
import com.mds.orderapi.dto.OrderResponse;
import com.mds.orderapi.event.OrderPlacedEvent;
import com.mds.orderapi.event.ReservationResultEvent;
import com.mds.orderapi.exception.InvalidEventException;
import com.mds.orderapi.exception.OrderNotFoundException;
import com.mds.orderapi.model.Order;
import com.mds.orderapi.model.OrderStatus;
import com.mds.orderapi.model.OutboxEvent;
import com.mds.orderapi.repository.OrderRepository;
import com.mds.orderapi.repository.OutboxRepository;
import com.mds.orderapi.util.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxRepository, objectMapper);
    }

    @Test
    void placeOrder_newOrder_savesPendingOrderAndOutboxEventInSameCall() {
        when(orderRepository.findById("o-1")).thenReturn(Optional.empty());

        OrderResponse response;
        try (var ignored = CorrelationId.bind("corr-1")) {
            response = orderService.placeOrder(new CreateOrderRequest("o-1", "item-1", 2));
        }

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(order.capture());
        assertThat(order.getValue().getOrderId()).isEqualTo("o-1");
        assertThat(order.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);

        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getAggregateId()).isEqualTo("o-1");
        assertThat(event.getValue().getEventType()).isEqualTo(OrderPlacedEvent.TYPE);
        assertThat(event.getValue().getSchemaVersion()).isEqualTo(OrderPlacedEvent.SCHEMA_VERSION);
        assertThat(event.getValue().getCorrelationId()).isEqualTo("corr-1");

        JsonNode payload = objectMapper.readTree(event.getValue().getPayload());
        assertThat(payload.get("orderId").asString()).isEqualTo("o-1");
        assertThat(payload.get("itemId").asString()).isEqualTo("item-1");
        assertThat(payload.get("quantity").asInt()).isEqualTo(2);
    }

    @Test
    void placeOrder_existingOrderId_returnsStoredOrderWithoutNewEvent() {
        Order stored = order("o-1", OrderStatus.RESERVED);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(stored));

        // Payload differs from the stored order: orderId alone decides idempotency.
        OrderResponse response = orderService.placeOrder(new CreateOrderRequest("o-1", "other-item", 99));

        assertThat(response.itemId()).isEqualTo("item-1");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(OrderStatus.RESERVED);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void getOrder_unknownId_throwsNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("missing"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void applyReservationResult_reserved_marksOrderReserved() {
        Order pending = order("o-1", OrderStatus.PENDING);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(pending));

        orderService.applyReservationResult(result("o-1", ReservationResultEvent.Status.RESERVED, null));

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(pending.getStatusReason()).isNull();
    }

    @Test
    void applyReservationResult_rejected_marksOrderRejectedWithReason() {
        Order pending = order("o-1", OrderStatus.PENDING);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(pending));

        orderService.applyReservationResult(result("o-1", ReservationResultEvent.Status.REJECTED, "insufficient stock"));

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(pending.getStatusReason()).isEqualTo("insufficient stock");
    }

    @Test
    void applyReservationResult_orderAlreadyFinal_ignoresDuplicate() {
        Order reserved = order("o-1", OrderStatus.RESERVED);
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(reserved));

        orderService.applyReservationResult(result("o-1", ReservationResultEvent.Status.REJECTED, "late duplicate"));

        assertThat(reserved.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(reserved.getStatusReason()).isNull();
    }

    @Test
    void applyReservationResult_unknownOrder_throwsInvalidEvent() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.applyReservationResult(
                result("missing", ReservationResultEvent.Status.RESERVED, null)))
                .isInstanceOf(InvalidEventException.class);
    }

    private static Order order(String orderId, OrderStatus status) {
        return Order.builder().orderId(orderId).itemId("item-1").quantity(2).status(status).build();
    }

    private static ReservationResultEvent result(String orderId, ReservationResultEvent.Status status, String reason) {
        return new ReservationResultEvent(orderId, status, reason, null);
    }
}
