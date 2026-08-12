package com.skytrace.backend.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeviceTelemetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DeviceTelemetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DeviceTelemetryEvent event) {
        rabbitTemplate.convertAndSend(
                MessagingProperties.REALTIME_EXCHANGE,
                "",
                event
        );
    }
}
