package com.mds.inventoryservice.service;

import com.mds.inventoryservice.event.OrderPlacedEvent;
import com.mds.inventoryservice.event.ReservationResultEvent;
import com.mds.inventoryservice.model.OutboxEvent;
import com.mds.inventoryservice.model.Reservation;
import com.mds.inventoryservice.model.ReservationStatus;
import com.mds.inventoryservice.repository.OutboxRepository;
import com.mds.inventoryservice.repository.ReservationRepository;
import com.mds.inventoryservice.repository.StockRepository;
import com.mds.inventoryservice.util.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(stockRepository, reservationRepository, outboxRepository, objectMapper, meterRegistry);
    }

    @Test
    void enoughStock_reservesAndWritesResultToOutbox() {
        when(stockRepository.tryReserve("item-1", 2)).thenReturn(1);

        try (var ignored = CorrelationId.bind("corr-1")) {
            service.reserve(order("o-1", "item-1", 2), "m-1");
        }

        Reservation reservation = savedReservation();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getReason()).isNull();
        assertThat(reservation.getMessageId()).isEqualTo("m-1");

        OutboxEvent event = savedOutboxEvent();
        assertThat(event.getAggregateId()).isEqualTo("o-1");
        assertThat(event.getEventType()).isEqualTo(ReservationResultEvent.TYPE);
        assertThat(event.getSchemaVersion()).isEqualTo(ReservationResultEvent.SCHEMA_VERSION);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("orderId").asString()).isEqualTo("o-1");
        assertThat(payload.get("status").asString()).isEqualTo("RESERVED");
        assertThat(meterRegistry.counter("inventory.reservations", "status", "RESERVED").count()).isEqualTo(1);
    }

    @Test
    void notEnoughStock_rejectsWithInsufficientStock() {
        when(stockRepository.tryReserve("item-1", 50)).thenReturn(0);
        when(stockRepository.existsById("item-1")).thenReturn(true);

        service.reserve(order("o-1", "item-1", 50), "m-1");

        Reservation reservation = savedReservation();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getReason()).isEqualTo(Reservation.REASON_INSUFFICIENT_STOCK);

        JsonNode payload = objectMapper.readTree(savedOutboxEvent().getPayload());
        assertThat(payload.get("status").asString()).isEqualTo("REJECTED");
        assertThat(payload.get("reason").asString()).isEqualTo(Reservation.REASON_INSUFFICIENT_STOCK);
    }

    @Test
    void unknownItem_rejectsWithUnknownItem() {
        when(stockRepository.tryReserve("nope", 1)).thenReturn(0);
        when(stockRepository.existsById("nope")).thenReturn(false);

        service.reserve(order("o-1", "nope", 1), "m-1");

        assertThat(savedReservation().getReason()).isEqualTo(Reservation.REASON_UNKNOWN_ITEM);
    }

    @Test
    void alreadyProcessedOrder_isSkippedWithoutTouchingStock() {
        when(reservationRepository.existsById("o-1")).thenReturn(true);

        service.reserve(order("o-1", "item-1", 2), "m-2");

        verify(stockRepository, never()).tryReserve(anyString(), anyInt());
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(outboxRepository, never()).save(any());
        assertThat(meterRegistry.counter("inventory.orders.duplicate").count()).isEqualTo(1);
    }

    private Reservation savedReservation() {
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private OutboxEvent savedOutboxEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private static OrderPlacedEvent order(String orderId, String itemId, int quantity) {
        return new OrderPlacedEvent(orderId, itemId, quantity, null);
    }
}
