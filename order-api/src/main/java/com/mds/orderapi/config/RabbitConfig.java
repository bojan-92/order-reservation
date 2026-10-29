package com.mds.orderapi.config;

import com.mds.orderapi.exception.InvalidEventException;
import org.springframework.amqp.core.*;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange ordersExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().exchange()).durable(true).build();
    }

    @Bean
    public TopicExchange inventoryExchange(AppProperties props) {
        return ExchangeBuilder.topicExchange(props.messaging().inventoryExchange()).durable(true).build();
    }

    @Bean
    public Queue reservationResultsQueue(AppProperties props) {
        var m = props.messaging();
        return QueueBuilder.durable(m.reservationResultsQueue())
                .deadLetterExchange(m.deadLetterExchange())
                .deadLetterRoutingKey(m.reservationResultsQueue())
                .build();
    }

    @Bean
    public Binding reservationResultsBinding(Queue reservationResultsQueue, TopicExchange inventoryExchange,
                                             AppProperties props) {
        return BindingBuilder.bind(reservationResultsQueue)
                .to(inventoryExchange)
                .with(props.messaging().reservationResultRoutingKey());
    }

    @Bean
    public DirectExchange deadLetterExchange(AppProperties props) {
        return ExchangeBuilder.directExchange(props.messaging().deadLetterExchange()).durable(true).build();
    }

    @Bean
    public Queue reservationResultsDlq(AppProperties props) {
        return QueueBuilder.durable(props.messaging().reservationResultsDlq()).build();
    }

    @Bean
    public Binding reservationResultsDlqBinding(Queue reservationResultsDlq, DirectExchange deadLetterExchange,
                                                AppProperties props) {
        return BindingBuilder.bind(reservationResultsDlq)
                .to(deadLetterExchange)
                .with(props.messaging().reservationResultsQueue());
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer skipRetryForInvalidEvents() {
        return settings -> settings.setExceptionPredicate(ex -> !isCausedBy(ex, InvalidEventException.class));
    }

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