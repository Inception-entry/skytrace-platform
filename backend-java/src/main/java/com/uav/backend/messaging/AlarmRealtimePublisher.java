package com.uav.backend.messaging;

import com.uav.backend.alarm.dto.AlarmResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AlarmRealtimePublisher {

    private final RabbitTemplate rabbitTemplate;

    public AlarmRealtimePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishCreated(AlarmResponse alarm) {
        rabbitTemplate.convertAndSend(
                MessagingProperties.REALTIME_EXCHANGE,
                "",
                new AlarmCreatedEvent(
                        alarm.id(),
                        alarm.eventCode(),
                        alarm.deviceCode(),
                        alarm.taskCode(),
                        alarm.eventType(),
                        alarm.weaponType(),
                        alarm.confidence(),
                        alarm.latitude(),
                        alarm.longitude(),
                        alarm.imageUrl(),
                        alarm.videoUrl(),
                        alarm.status(),
                        alarm.eventTime()
                )
        );
    }
}
