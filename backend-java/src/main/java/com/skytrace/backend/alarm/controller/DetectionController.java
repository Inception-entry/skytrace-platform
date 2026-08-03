package com.skytrace.backend.alarm.controller;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.messaging.DetectionAlarmMessage;
import com.skytrace.backend.messaging.DetectionAlarmPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/detections")
@ConditionalOnBean(DetectionAlarmPublisher.class)
public class DetectionController {

    private final DetectionAlarmPublisher publisher;

    public DetectionController(DetectionAlarmPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/alarms")
    public ApiResponse<Map<String, String>> publish(
            @Valid @RequestBody PublishDetectionRequest request) {
        publisher.publish(new DetectionAlarmMessage(
                request.deviceCode(),
                request.taskCode(),
                request.eventType(),
                request.weaponType(),
                request.confidence(),
                request.latitude(),
                request.longitude(),
                request.imageObjectKey(),
                request.videoObjectKey(),
                request.eventTime() == null
                        ? LocalDateTime.now()
                        : request.eventTime()
        ));
        return ApiResponse.ok(Map.of(
                "status", "queued",
                "exchange", "skytrace.detection"
        ));
    }

    public record PublishDetectionRequest(
            @NotBlank String deviceCode,
            String taskCode,
            @NotBlank String eventType,
            String weaponType,
            BigDecimal confidence,
            BigDecimal latitude,
            BigDecimal longitude,
            String imageObjectKey,
            String videoObjectKey,
            LocalDateTime eventTime
    ) {
    }
}
