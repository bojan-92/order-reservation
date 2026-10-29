package com.mds.inventoryservice.service;

import com.mds.inventoryservice.dto.ReservationResponse;
import com.mds.inventoryservice.event.OrderPlacedEvent;
import com.mds.inventoryservice.event.ReservationResultEvent;
import com.mds.inventoryservice.exception.NotFoundException;
import com.mds.inventoryservice.model.OutboxEvent;
import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;
import com.mds.inventoryservice.repository.OutboxRepository;
import com.mds.inventoryservice.repository.ReservationRepository;
import com.mds.inventoryservice.repository.StockRepository;
import com.mds.inventoryservice.util.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Reservation use cases: reserving stock for placed orders and looking up the outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Reserves stock for the order, or rejects it, and saves the result event in the same transaction
     * (transactional outbox). An order that was already processed is skipped, so redelivered events are harmless.
     *
     * @param event     validated OrderPlaced event
     * @param messageId broker message id, stored for tracing
     */
    @Transactional
    public void reserve(OrderPlacedEvent event, String messageId) {
        if (reservationRepository.existsById(event.orderId())) {
            log.info("Order {} already processed, ignoring duplicate (messageId={})", event.orderId(), messageId);
            meterRegistry.counter("inventory.orders.duplicate").increment();
            return;
        }

        boolean reserved = stockRepository.tryReserve(event.itemId(), event.quantity()) == 1;
        Reservation reservation = Reservation.builder()
                .orderId(event.orderId())
                .itemId(event.itemId())
                .quantity(event.quantity())
                .status(reserved ? ReservationStatus.RESERVED : ReservationStatus.REJECTED)
                .reason(reserved ? null : rejectionReason(event.itemId()))
                .messageId(messageId)
                .build();
        reservationRepository.saveAndFlush(reservation);

        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(reservation.getOrderId())
                .eventType(ReservationResultEvent.TYPE)
                .schemaVersion(ReservationResultEvent.SCHEMA_VERSION)
                .payload(objectMapper.writeValueAsString(ReservationResultEvent.from(reservation)))
                .correlationId(CorrelationId.current())
                .build());

        meterRegistry.counter("inventory.reservations", "status", reservation.getStatus().name()).increment();
        log.info("Order {} {} (item={}, qty={}{})", reservation.getOrderId(), reservation.getStatus(),
                reservation.getItemId(), reservation.getQuantity(),
                reserved ? "" : ", reason=" + reservation.getReason());
    }

    /**
     * Looks up the reservation outcome for an order.
     *
     * @param orderId order id from the OrderPlaced event
     * @return the reservation
     * @throws NotFoundException if the order hasn't been processed
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String orderId) {
        return reservationRepository.findById(orderId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new NotFoundException("No reservation for order '" + orderId + "'"));
    }

    /**
     * Works out why stock could not be reserved.
     *
     * @param itemId requested item
     * @return {@link Reservation#REASON_INSUFFICIENT_STOCK} if the item exists, otherwise
     * {@link Reservation#REASON_UNKNOWN_ITEM}
     */
    private String rejectionReason(String itemId) {
        return stockRepository.existsById(itemId)
                ? Reservation.REASON_INSUFFICIENT_STOCK
                : Reservation.REASON_UNKNOWN_ITEM;
    }
}
