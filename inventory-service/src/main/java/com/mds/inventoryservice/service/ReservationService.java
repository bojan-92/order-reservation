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
     * Reserves stock for an order exactly once. The stock change, the reservation record and the result event
     * are committed in one transaction (transactional outbox), so a redelivered OrderPlaced either finds the
     * reservation and is skipped, or the previous attempt rolled back completely and is redone.
     * <p>
     * Two concurrent deliveries of the same order both pass the existence check, but only one can insert the
     * reservation row; the other fails on the primary key, is retried by the listener and is then skipped.
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

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String orderId) {
        return reservationRepository.findById(orderId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new NotFoundException("No reservation for order '" + orderId + "'"));
    }

    private String rejectionReason(String itemId) {
        return stockRepository.existsById(itemId)
                ? Reservation.REASON_INSUFFICIENT_STOCK
                : Reservation.REASON_UNKNOWN_ITEM;
    }
}
