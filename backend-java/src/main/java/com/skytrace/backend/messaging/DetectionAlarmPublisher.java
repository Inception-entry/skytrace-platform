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
public class DetectionAlarmPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DetectionAlarmPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(DetectionAlarmMessage message) {
        rabbitTemplate.convertAndSend(
                MessagingProperties.DETECTION_EXCHANGE,
                MessagingProperties.DETECTION_ROUTING_KEY,
                message
        );
    }
}
