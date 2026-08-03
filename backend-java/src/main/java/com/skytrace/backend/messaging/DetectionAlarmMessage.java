package com.skytrace.backend.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DetectionAlarmMessage(
        String deviceCode,
        String taskCode,
        String eventType,
        String weaponType,
        BigDecimal confidence,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageObjectKey,
        String videoObjectKey,
        LocalDateTime eventTime
) {
}
