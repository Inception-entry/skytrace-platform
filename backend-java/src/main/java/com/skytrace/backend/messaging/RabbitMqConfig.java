package com.skytrace.backend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RabbitMqConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public DirectExchange detectionExchange() {
        return new DirectExchange(MessagingProperties.DETECTION_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange detectionDlx() {
        return new DirectExchange(MessagingProperties.DETECTION_DLX, true, false);
    }

    @Bean
    public Queue detectionDlq() {
        return QueueBuilder.durable(MessagingProperties.DETECTION_DLQ).build();
    }

    @Bean
    public Binding detectionDlqBinding(
            Queue detectionDlq,
            DirectExchange detectionDlx) {
        return BindingBuilder
                .bind(detectionDlq)
                .to(detectionDlx)
                .with(MessagingProperties.DETECTION_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue detectionQueue() {
        return QueueBuilder.durable(MessagingProperties.DETECTION_QUEUE)
                .deadLetterExchange(MessagingProperties.DETECTION_DLX)
                .deadLetterRoutingKey(MessagingProperties.DETECTION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding detectionBinding(
            Queue detectionQueue,
            DirectExchange detectionExchange) {
        return BindingBuilder
                .bind(detectionQueue)
                .to(detectionExchange)
                .with(MessagingProperties.DETECTION_ROUTING_KEY);
    }

    @Bean
    public FanoutExchange realtimeExchange() {
        return new FanoutExchange(MessagingProperties.REALTIME_EXCHANGE, true, false);
    }

    @Bean
    public Queue realtimeQueue() {
        return new Queue(MessagingProperties.REALTIME_QUEUE, true);
    }

    @Bean
    public Binding realtimeBinding(
            Queue realtimeQueue,
            FanoutExchange realtimeExchange) {
        return BindingBuilder.bind(realtimeQueue).to(realtimeExchange);
    }
}
