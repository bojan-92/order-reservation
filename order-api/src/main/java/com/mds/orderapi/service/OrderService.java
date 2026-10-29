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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Order use cases: placing orders and applying reservation results from the inventory service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Saves the order and its OrderPlaced event in one transaction (transactional outbox),
     * so an accepted order is never lost and never published without being stored. If the {@code orderId}
     * already exists, the stored order is returned and nothing is published again.
     *
     * @param request validated order payload
     * @return the new or already stored order
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        Optional<Order> existing = orderRepository.findById(request.orderId());
        if (existing.isPresent()) {
            log.info("Order {} already exists", request.orderId());
            return OrderResponse.from(existing.get());
        }

        Order order = Order.builder()
                .orderId(request.orderId())
                .itemId(request.itemId())
                .quantity(request.quantity())
                .status(OrderStatus.PENDING)
                .build();
        orderRepository.saveAndFlush(order);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(order.getOrderId())
                .eventType(OrderPlacedEvent.TYPE)
                .schemaVersion(OrderPlacedEvent.SCHEMA_VERSION)
                .payload(objectMapper.writeValueAsString(OrderPlacedEvent.from(order)))
                .correlationId(CorrelationId.current())
                .build());

        log.info("Order {} accepted (item={}, qty={})", order.getOrderId(), order.getItemId(), order.getQuantity());
        return OrderResponse.from(order);
    }

    /**
     * Looks up an order.
     *
     * @param orderId client-supplied order id
     * @return the order
     * @throws OrderNotFoundException if no order has this id
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Moves a pending order to {@code RESERVED} or {@code REJECTED}. Results for orders that are already
     * final are ignored, so redelivered messages are harmless.
     *
     * @param event reservation result from the inventory service
     * @throws InvalidEventException if the order doesn't exist
     */
    @Transactional
    public void applyReservationResult(ReservationResultEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new InvalidEventException("Result for unknown order '" + event.orderId() + "'"));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Duplicate result for order {} ignored. Order already in {} status", order.getOrderId(), order.getStatus());
            return;
        }

        order.setStatus(event.status() == ReservationResultEvent.Status.RESERVED
                ? OrderStatus.RESERVED
                : OrderStatus.REJECTED);
        order.setStatusReason(event.reason());

        log.info("Order {} -> {}", order.getOrderId(), order.getStatus());
    }
}
