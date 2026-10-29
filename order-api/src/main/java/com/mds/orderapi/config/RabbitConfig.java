package com.mds.orderapi.config;

import com.mds.orderapi.exception.InvalidEventException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by this service, plus retry and returns handling.
 * <p>
 * The service declares the exchange it publishes to and the queue it consumes from. The queue for OrderPlaced
 * events is owned and declared by the inventory service.
 */
@Configuration
public class RabbitConfig {

    /**
     * Durable exchange that OrderPlaced events are published to.
     *
     * @param props application settings
     * @return the orders exchange
     */
    @Bean
    public TopicExchange ordersExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().exchange()).durable(true).build();
    }

    /**
     * Durable exchange the inventory service publishes reservation results to. Declared here too so the
     * binding below works regardless of which service starts first.
     *
     * @param props application settings
     * @return the inventory exchange
     */
    @Bean
    public TopicExchange inventoryExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().inventoryExchange()).durable(true).build();
    }

    /**
     * Durable queue of reservation results. Rejected messages are dead-lettered instead of dropped or requeued.
     *
     * @param props application settings
     * @return the reservation results queue
     */
    @Bean
    public Queue reservationResultsQueue(AppProperties props) {
        var m = props.messaging();
        return QueueBuilder.durable(m.reservationResultsQueue())
                .deadLetterExchange(m.deadLetterExchange())
                .deadLetterRoutingKey(m.reservationResultsQueue())
                .build();
    }

    /**
     * Routes reservation results from the inventory exchange to this service's queue.
     *
     * @param reservationResultsQueue queue to bind
     * @param inventoryExchange       exchange to bind to
     * @param props                   application settings
     * @return the binding
     */
    @Bean
    public Binding reservationResultsBinding(Queue reservationResultsQueue, TopicExchange inventoryExchange,
                                             AppProperties props) {
        return BindingBuilder.bind(reservationResultsQueue)
                .to(inventoryExchange)
                .with(props.messaging().reservationResultRoutingKey());
    }

    /**
     * Exchange that receives reservation results this service rejected.
     *
     * @param props application settings
     * @return the dead-letter exchange
     */
    @Bean
    public DirectExchange deadLetterExchange(AppProperties props) {
        return ExchangeBuilder.directExchange(props.messaging().deadLetterExchange()).durable(true).build();
    }

    /**
     * Dead-letter queue keeping rejected reservation results for inspection or replay.
     *
     * @param props application settings
     * @return the dead-letter queue
     */
    @Bean
    public Queue reservationResultsDlq(AppProperties props) {
        return QueueBuilder.durable(props.messaging().reservationResultsDlq()).build();
    }

    /**
     * Routes dead-lettered reservation results into the dead-letter queue.
     *
     * @param reservationResultsDlq dead-letter queue
     * @param deadLetterExchange    dead-letter exchange
     * @param props                 application settings
     * @return the binding
     */
    @Bean
    public Binding reservationResultsDlqBinding(Queue reservationResultsDlq, DirectExchange deadLetterExchange,
                                                AppProperties props) {
        return BindingBuilder.bind(reservationResultsDlq)
                .to(deadLetterExchange)
                .with(props.messaging().reservationResultsQueue());
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