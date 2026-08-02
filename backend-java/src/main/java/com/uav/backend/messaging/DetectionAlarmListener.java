package com.uav.backend.messaging;

import com.uav.backend.alarm.dto.AlarmResponse;
import com.uav.backend.alarm.dto.CreateAlarmRequest;
import com.uav.backend.alarm.service.AlarmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DetectionAlarmListener {

    private static final Logger log = LoggerFactory.getLogger(
            DetectionAlarmListener.class
    );

    private final AlarmService alarmService;
    private final AlarmRealtimePublisher realtimePublisher;

    public DetectionAlarmListener(
            AlarmService alarmService,
            AlarmRealtimePublisher realtimePublisher) {
        this.alarmService = alarmService;
        this.realtimePublisher = realtimePublisher;
    }

    @RabbitListener(queues = MessagingProperties.DETECTION_QUEUE)
    public void onDetection(DetectionAlarmMessage message) {
        LocalDateTime eventTime = message.eventTime() == null
                ? LocalDateTime.now()
                : message.eventTime();
        AlarmResponse alarm = alarmService.create(new CreateAlarmRequest(
                message.deviceCode(),
                message.taskCode(),
                message.eventType(),
                message.weaponType(),
                message.confidence(),
                message.latitude(),
                message.longitude(),
                message.imageObjectKey(),
                message.videoObjectKey(),
                eventTime
        ), true, false);
        realtimePublisher.publishCreated(alarm);
        log.info(
                "event=detection_alarm_consumed eventCode={} taskCode={}",
                alarm.eventCode(),
                alarm.taskCode()
        );
    }
}
