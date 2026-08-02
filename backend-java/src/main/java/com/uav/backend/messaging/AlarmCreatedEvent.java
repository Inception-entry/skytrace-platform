package com.uav.backend.messaging;

import com.uav.backend.alarm.domain.AlarmStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlarmCreatedEvent(
        Long id,
        String eventCode,
        String deviceCode,
        String taskCode,
        String eventType,
        String weaponType,
        BigDecimal confidence,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        String videoUrl,
        AlarmStatus status,
        LocalDateTime eventTime
) {
}
