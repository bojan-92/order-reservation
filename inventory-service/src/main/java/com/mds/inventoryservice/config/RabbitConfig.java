package com.mds.inventoryservice.config;

import com.mds.inventoryservice.exception.InvalidEventException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by this service, plus retry and returns handling.
 * <p>
 * The service declares the exchange it publishes to and the queue it consumes from. The queue for reservation
 * results is owned and declared by order-api.
 */
@Configuration
public class RabbitConfig {

    /**
     * Durable exchange order-api publishes OrderPlaced events to. Declared here too so the binding below works
     * regardless of which service starts first.
     *
     * @param props application settings
     * @return the orders exchange
     */
    @Bean
    public TopicExchange ordersExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().ordersExchange()).durable(true).build();
    }

    /**
     * Durable exchange that reservation results are published to.
     *
     * @param props application settings
     * @return the inventory exchange
     */
    @Bean
    public TopicExchange inventoryExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().inventoryExchange()).durable(true).build();
    }

    /**
     * Durable queue of OrderPlaced events. Rejected messages are dead-lettered instead of dropped or requeued.
     *
     * @param props application settings
     * @return the OrderPlaced queue
     */
    @Bean
    public Queue orderPlacedQueue(AppProperties props) {
        var m = props.messaging();
        return QueueBuilder.durable(m.orderPlacedQueue())
                .deadLetterExchange(m.deadLetterExchange())
                .deadLetterRoutingKey(m.orderPlacedQueue())
                .build();
    }

    /**
     * Routes OrderPlaced events from the orders exchange to this service's queue.
     *
     * @param orderPlacedQueue queue to bind
     * @param ordersExchange   exchange to bind to
     * @param props            application settings
     * @return the binding
     */
    @Bean
    public Binding orderPlacedBinding(Queue orderPlacedQueue, TopicExchange ordersExchange, AppProperties props) {
        return BindingBuilder.bind(orderPlacedQueue)
                .to(ordersExchange)
                .with(props.messaging().orderPlacedRoutingKey());
    }

    /**
     * Exchange that receives OrderPlaced events this service rejected.
     *
     * @param props application settings
     * @return the dead-letter exchange
     */
    @Bean
    public DirectExchange deadLetterExchange(AppProperties props) {
        return ExchangeBuilder.directExchange(props.messaging().deadLetterExchange()).durable(true).build();
    }

    /**
     * Dead-letter queue keeping rejected OrderPlaced events for inspection or replay.
     *
     * @param props application settings
     * @return the dead-letter queue
     */
    @Bean
    public Queue orderPlacedDlq(AppProperties props) {
        return QueueBuilder.durable(props.messaging().orderPlacedDlq()).build();
    }

    /**
     * Routes dead-lettered OrderPlaced events into the dead-letter queue.
     *
     * @param orderPlacedDlq     dead-letter queue
     * @param deadLetterExchange dead-letter exchange
     * @param props              application settings
     * @return the binding
     */
    @Bean
    public Binding orderPlacedDlqBinding(Queue orderPlacedDlq, DirectExchange deadLetterExchange, AppProperties props) {
        return BindingBuilder.bind(orderPlacedDlq)
                .to(deadLetterExchange)
                .with(props.messaging().orderPlacedQueue());
    }

    /**
     * Skips listener retries for {@link InvalidEventException}: a malformed or unknown event will never
     * succeed, so it goes straight to the dead-letter queue. Transient errors are still retried with backoff.
     *
     * @return retry settings customizer
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForInvalidEvents() {
        return settings -> settings.setExceptionPredicate(ex -> !isCausedBy(ex, InvalidEventException.class));
    }

    /**
     * Installs a no-op returns callback. Unroutable (mandatory) messages are handled by the outbox relay
     * through {@code CorrelationData#getReturned()}.
     *
     * @return template customizer
     */
    @Bean
    public RabbitTemplateCustomizer returnsCallbackCustomizer() {
        return template -> template.setReturnsCallback(returned -> { });
    }

    /**
     * Checks whether the exception or any of its causes is of the given type.
     *
     * @param ex   exception to inspect
     * @param type type to look for in the cause chain
     * @return {@code true} if a matching cause is found
     */
    private static boolean isCausedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
