package com.mds.orderapi;

import com.mds.orderapi.config.AppProperties;
import com.mds.orderapi.event.ReservationResultEvent;
import com.mds.orderapi.model.OrderStatus;
import com.mds.orderapi.model.OutboxEvent;
import com.mds.orderapi.repository.OrderRepository;
import com.mds.orderapi.repository.OutboxRepository;
import com.mds.orderapi.util.CorrelationId;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through real Postgres and RabbitMQ: HTTP -> DB + outbox -> broker, and broker -> listener -> DB.
 * A test queue bound to the orders exchange stands in for the inventory service.
 */
@SpringBootTest(properties = "app.outbox.relay-interval-ms=100")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ApiIntegrationTest.InventoryStub.class})
class ApiIntegrationTest {

    private static final String ORDER_PLACED_QUEUE = "test.inventory.order-placed";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TestConfiguration
    static class InventoryStub {

        @Bean
        Queue orderPlacedQueue() {
            return new Queue(ORDER_PLACED_QUEUE, false);
        }

        @Bean
        Binding orderPlacedBinding(Queue orderPlacedQueue, TopicExchange ordersExchange, AppProperties props) {
            return BindingBuilder.bind(orderPlacedQueue).to(ordersExchange).with(props.messaging().orderPlacedRoutingKey());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties props;

    @Test
    void postOrder_storesOrderAndPublishesOrderPlacedEvent() throws Exception {
        String orderId = newOrderId();

        mockMvc.perform(post("/orders")
                        .header(CorrelationId.HTTP_HEADER, "corr-" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(orderId, "item-1", 2)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(CorrelationId.HTTP_HEADER, "corr-" + orderId))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Message message = awaitOrderPlaced(orderId);
        var messageProps = message.getMessageProperties();
        assertThat(messageProps.getType()).isEqualTo("OrderPlaced");
        assertThat(messageProps.getHeader("schemaVersion").toString()).isEqualTo("1");
        assertThat(messageProps.getCorrelationId()).isEqualTo("corr-" + orderId);
        assertThat(messageProps.getMessageId()).isNotBlank();

        JsonNode body = objectMapper.readTree(message.getBody());
        assertThat(body.get("itemId").asString()).isEqualTo("item-1");
        assertThat(body.get("quantity").asInt()).isEqualTo(2);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(outboxFor(orderId)).singleElement()
                        .satisfies(e -> assertThat(e.getPublishedAt()).isNotNull()));
    }

    @Test
    void postOrder_sameOrderIdTwice_returnsStoredOrderAndCreatesSingleEvent() throws Exception {
        String orderId = newOrderId();

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson(orderId, "item-1", 2)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson(orderId, "item-9", 7)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.itemId").value("item-1"))
                .andExpect(jsonPath("$.quantity").value(2));

        assertThat(outboxFor(orderId)).hasSize(1);
    }

    @Test
    void postOrder_invalidPayload_returns400() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson(newOrderId(), "item-1", 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson("x".repeat(65), "item-1", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/orders/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reservationResult_updatesOrderStatus() throws Exception {
        String orderId = newOrderId();
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson(orderId, "item-1", 2)))
                .andExpect(status().isAccepted());

        sendReservationResult(ReservationResultEvent.TYPE, """
                {"orderId":"%s","status":"REJECTED","reason":"insufficient stock"}
                """.formatted(orderId));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId)).get()
                        .satisfies(o -> {
                            assertThat(o.getStatus()).isEqualTo(OrderStatus.REJECTED);
                            assertThat(o.getStatusReason()).isEqualTo("insufficient stock");
                        }));

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reservationResult_forUnknownOrder_goesToDeadLetterQueue() {
        String orderId = newOrderId();

        sendReservationResult(ReservationResultEvent.TYPE, """
                {"orderId":"%s","status":"RESERVED"}
                """.formatted(orderId));

        Message dead = awaitMessage(props.messaging().reservationResultsDlq(),
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(orderId));
        assertThat(dead.getMessageProperties().getType()).isEqualTo(ReservationResultEvent.TYPE);
    }

    @Test
    void reservationResult_malformedJson_goesToDeadLetterQueue() {
        String marker = newOrderId();

        sendReservationResult(ReservationResultEvent.TYPE, "this is not json " + marker);

        awaitMessage(props.messaging().reservationResultsDlq(),
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(marker));
    }

    private void sendReservationResult(String type, String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setType(type)
                .setHeader("schemaVersion", ReservationResultEvent.SCHEMA_VERSION)
                .setMessageId(UUID.randomUUID().toString())
                .build();
        rabbitTemplate.send(props.messaging().inventoryExchange(), props.messaging().reservationResultRoutingKey(), message);
    }

    private Message awaitOrderPlaced(String orderId) {
        return awaitMessage(ORDER_PLACED_QUEUE,
                m -> orderId.equals(objectMapper.readTree(m.getBody()).get("orderId").asString()));
    }

    /**
     * Queues are shared between tests, so skip messages produced by other tests.
     */
    private Message awaitMessage(String queue, Predicate<Message> matches) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null && matches.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No matching message on " + queue + " within " + TIMEOUT);
    }

    private List<OutboxEvent> outboxFor(String orderId) {
        return outboxRepository.findAll().stream().filter(e -> e.getAggregateId().equals(orderId)).toList();
    }

    private static String newOrderId() {
        return "it-" + UUID.randomUUID();
    }

    private static String orderJson(String orderId, String itemId, int quantity) {
        return """
                {"orderId":"%s","itemId":"%s","quantity":%d}
                """.formatted(orderId, itemId, quantity);
    }
}
