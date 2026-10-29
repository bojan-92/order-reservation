package com.mds.inventoryservice.config;

import com.mds.inventoryservice.exception.InvalidEventException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange ordersExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().ordersExchange()).durable(true).build();
    }

    @Bean
    public TopicExchange inventoryExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().inventoryExchange()).durable(true).build();
    }

    @Bean
    public Queue orderPlacedQueue(AppProperties props) {
        var m = props.messaging();
        return QueueBuilder.durable(m.orderPlacedQueue())
                .deadLetterExchange(m.deadLetterExchange())
                .deadLetterRoutingKey(m.orderPlacedQueue())
                .build();
    }

    @Bean
    public Binding orderPlacedBinding(Queue orderPlacedQueue, TopicExchange ordersExchange, AppProperties props) {
        return BindingBuilder.bind(orderPlacedQueue)
                .to(ordersExchange)
                .with(props.messaging().orderPlacedRoutingKey());
    }

    @Bean
    public DirectExchange deadLetterExchange(AppProperties props) {
        return ExchangeBuilder.directExchange(props.messaging().deadLetterExchange()).durable(true).build();
    }

    @Bean
    public Queue orderPlacedDlq(AppProperties props) {
        return QueueBuilder.durable(props.messaging().orderPlacedDlq()).build();
    }

    @Bean
    public Binding orderPlacedDlqBinding(Queue orderPlacedDlq, DirectExchange deadLetterExchange, AppProperties props) {
        return BindingBuilder.bind(orderPlacedDlq)
                .to(deadLetterExchange)
                .with(props.messaging().orderPlacedQueue());
    }

    /**
     * Invalid events will never succeed, so they go straight to the DLQ instead of burning retries.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForInvalidEvents() {
        return settings -> settings.setExceptionPredicate(ex -> !isCausedBy(ex, InvalidEventException.class));
    }

    /**
     * Returns are read from CorrelationData by the outbox relay; the callback only has to exist for mandatory=true.
     */
    @Bean
    public RabbitTemplateCustomizer returnsCallbackCustomizer() {
        return template -> template.setReturnsCallback(returned -> { });
    }

    private static boolean isCausedBy(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
