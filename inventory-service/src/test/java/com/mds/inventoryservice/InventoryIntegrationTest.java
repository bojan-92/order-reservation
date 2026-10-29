package com.mds.inventoryservice;

import com.mds.inventoryservice.config.AppProperties;
import com.mds.inventoryservice.event.OrderPlacedEvent;
import com.mds.inventoryservice.event.ReservationResultEvent;
import com.mds.inventoryservice.model.ReservationStatus;
import com.mds.inventoryservice.model.StockItem;
import com.mds.inventoryservice.repository.ReservationRepository;
import com.mds.inventoryservice.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through real Postgres and RabbitMQ: OrderPlaced on the orders exchange -> listener -> DB + outbox
 * -> InventoryReservationResult on the inventory exchange. A test queue stands in for order-api.
 */
@SpringBootTest(properties = "app.outbox.relay-interval-ms=100")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, InventoryIntegrationTest.OrderApiStub.class})
class InventoryIntegrationTest {

    private static final String RESULTS_QUEUE = "test.order-api.reservation-results";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @TestConfiguration
    static class OrderApiStub {

        @Bean
        Queue resultsQueue() {
            return new Queue(RESULTS_QUEUE, false);
        }

        @Bean
        Binding resultsBinding(Queue resultsQueue, TopicExchange inventoryExchange, AppProperties props) {
            return BindingBuilder.bind(resultsQueue).to(inventoryExchange).with(props.messaging().reservationResultRoutingKey());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties props;

    @Test
    void orderWithEnoughStock_isReservedAndResultPublished() {
        String itemId = givenStock(5);
        String orderId = newId();

        sendOrderPlaced(orderId, itemId, 2, "corr-" + orderId);

        Message result = awaitResult(orderId);
        var messageProps = result.getMessageProperties();
        assertThat(messageProps.getType()).isEqualTo(ReservationResultEvent.TYPE);
        assertThat(messageProps.getHeader("schemaVersion").toString()).isEqualTo("1");
        assertThat(messageProps.getCorrelationId()).isEqualTo("corr-" + orderId);
        assertThat(objectMapper.readTree(result.getBody()).get("status").asString()).isEqualTo("RESERVED");

        assertThat(stockRepository.findById(itemId)).get().extracting(StockItem::getAvailable).isEqualTo(3);
    }

    @Test
    void orderWithInsufficientStock_isRejectedAndStockUntouched() {
        String itemId = givenStock(1);
        String orderId = newId();

        sendOrderPlaced(orderId, itemId, 2, null);

        JsonNode body = objectMapper.readTree(awaitResult(orderId).getBody());
        assertThat(body.get("status").asString()).isEqualTo("REJECTED");
        assertThat(body.get("reason").asString()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stockRepository.findById(itemId)).get().extracting(StockItem::getAvailable).isEqualTo(1);
    }

    @Test
    void redeliveredOrder_reservesStockOnlyOnce() {
        String itemId = givenStock(10);
        String orderId = newId();

        sendOrderPlaced(orderId, itemId, 3, null);
        sendOrderPlaced(orderId, itemId, 3, null);
        sendOrderPlaced(orderId, itemId, 3, null);

        awaitResult(orderId);
        // Give the duplicates time to be consumed before asserting nothing changed.
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(stockRepository.findById(itemId)).get().extracting(StockItem::getAvailable).isEqualTo(7));
        assertThat(reservationRepository.findAll().stream().filter(r -> r.getOrderId().equals(orderId))).hasSize(1);
    }

    @Test
    void concurrentOrdersForSameItem_neverOversell() {
        String itemId = givenStock(5);
        String prefix = newId();

        IntStream.range(0, 20).forEach(i -> sendOrderPlaced(prefix + "-" + i, itemId, 1, null));

        await().atMost(TIMEOUT).until(() ->
                reservationRepository.findAll().stream().filter(r -> r.getOrderId().startsWith(prefix)).count() == 20);
        var reservations = reservationRepository.findAll().stream().filter(r -> r.getOrderId().startsWith(prefix)).toList();
        assertThat(reservations).filteredOn(r -> r.getStatus() == ReservationStatus.RESERVED).hasSize(5);
        assertThat(stockRepository.findById(itemId)).get().extracting(StockItem::getAvailable).isEqualTo(0);
    }

    @Test
    void malformedMessage_goesToDeadLetterQueueWithoutRetry() {
        String marker = newId();

        send(OrderPlacedEvent.TYPE, "this is not json " + marker, null);

        awaitMessage(props.messaging().orderPlacedDlq(),
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(marker));
    }

    @Test
    void restApi_exposesStockItemsAndReservations() throws Exception {
        String itemId = givenStock(4);
        String orderId = newId();

        sendOrderPlaced(orderId, itemId, 4, null);
        awaitResult(orderId);

        mockMvc.perform(get("/reservations/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.itemId").value(itemId));
        mockMvc.perform(get("/stock-items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(0));
        mockMvc.perform(get("/stock-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemId == 'item-1')]").exists());

        mockMvc.perform(get("/reservations/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/stock-items/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String givenStock(int available) {
        String itemId = "it-item-" + UUID.randomUUID();
        stockRepository.saveAndFlush(StockItem.builder().itemId(itemId).available(available).build());
        return itemId;
    }

    private void sendOrderPlaced(String orderId, String itemId, int quantity, String correlationId) {
        send(OrderPlacedEvent.TYPE, """
                {"orderId":"%s","itemId":"%s","quantity":%d,"occurredAt":"2026-10-28T10:00:00Z"}
                """.formatted(orderId, itemId, quantity), correlationId);
    }

    private void send(String type, String body, String correlationId) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setType(type)
                .setHeader("schemaVersion", OrderPlacedEvent.SCHEMA_VERSION)
                .setMessageId(UUID.randomUUID().toString())
                .setCorrelationId(correlationId)
                .build();
        rabbitTemplate.send(props.messaging().ordersExchange(), props.messaging().orderPlacedRoutingKey(), message);
    }

    private Message awaitResult(String orderId) {
        return awaitMessage(RESULTS_QUEUE,
                m -> orderId.equals(objectMapper.readTree(m.getBody()).get("orderId").asString()));
    }

    /**
     * Queues are shared between tests, so skip messages produced by other tests.
     */
    private Message awaitMessage(String queue, Predicate<Message> matches) {
        AtomicReference<Message> found = new AtomicReference<>();
        await().atMost(TIMEOUT).until(() -> {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null && matches.test(message)) {
                found.set(message);
                return true;
            }
            return false;
        });
        return found.get();
    }

    private static String newId() {
        return "it-" + UUID.randomUUID();
    }
}
