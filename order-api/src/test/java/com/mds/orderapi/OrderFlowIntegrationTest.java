package com.mds.orderapi;

import com.jayway.jsonpath.JsonPath;
import com.mds.orderapi.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.outbox.relay-interval-ms=200")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderFlowIntegrationTest {

    private static final String TEST_QUEUE = "test.order-placed";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    OutboxRepository outboxRepository;

    @BeforeEach
    void bindTestQueue() {
        Queue queue = new Queue(TEST_QUEUE, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange("orders.exchange")).with("order.#"));
    }

    @Test
    void createOrder_isPublishedToRabbitThroughOutbox_withCorrelationId() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "it-corr-1")
                        .content(body("it-1", "item-1", 2)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-Id", "it-corr-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Message message = awaitOrderPlaced("it-1");
        MessageProperties props = message.getMessageProperties();

        assertThat(props.getType()).isEqualTo("OrderPlaced");
        assertThat(props.getCorrelationId()).isEqualTo("it-corr-1");
        assertThat((Object) props.getHeader("schemaVersion")).isEqualTo(1);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"itemId\":\"item-1\"")
                .contains("\"quantity\":2");
    }

    @Test
    void repeatedRequest_isIdempotentOnOrderId() throws Exception {
        String first = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("it-2", "item-1", 1)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("it-2", "item-1", 1)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // Same stored order comes back, not a new one.
        assertThat((String) JsonPath.read(second, "$.createdAt")).isEqualTo(JsonPath.read(first, "$.createdAt"));

        // orderId alone decides idempotency: a different payload still returns the stored order.
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("it-2", "item-1", 5)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quantity").value(1));

        long events = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals("it-2"))
                .count();
        assertThat(events).isEqualTo(1);
    }

    @Test
    void invalidRequest_returns400_unknownOrder_returns404() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "item-1", -1)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/orders/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reservationResult_updatesOrderStatus() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("it-3", "item-1", 1)))
                .andExpect(status().isAccepted());

        sendReservationResult("""
                {"orderId":"it-3","status":"REJECTED","reason":"INSUFFICIENT_STOCK","occurredAt":"2026-01-01T00:00:00Z"}""");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/orders/it-3"))
                        .andExpect(jsonPath("$.status").value("REJECTED"))
                        .andExpect(jsonPath("$.statusReason").value("INSUFFICIENT_STOCK")));
    }

    @Test
    void malformedReservationResult_goesToDlq() {
        sendReservationResult("this is not json");

        await().atMost(Duration.ofSeconds(10)).until(() ->
                rabbitTemplate.receive("order-api.reservation-results.dlq", 200) != null);
    }

    // ---------- helpers ----------

    private static String body(String orderId, String itemId, int quantity) {
        return """
                {"orderId":"%s","itemId":"%s","quantity":%d}""".formatted(orderId, itemId, quantity);
    }

    private void sendReservationResult(String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setType("InventoryReservationResult")
                .setHeader("schemaVersion", 1)
                .build();
        rabbitTemplate.send("inventory.exchange", "reservation.result", message);
    }

    /** Čita poruke iz test queue-a dok ne nađe OrderPlaced za traženi orderId. */
    private Message awaitOrderPlaced(String orderId) {
        AtomicReference<Message> found = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message m = rabbitTemplate.receive(TEST_QUEUE, 500);
            if (m != null && new String(m.getBody(), StandardCharsets.UTF_8).contains("\"orderId\":\"" + orderId + "\"")) {
                found.set(m);
                return true;
            }
            return false;
        });
        return found.get();
    }
}