package com.skytrace.backend.messaging;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.skytrace.backend.common.ShanghaiLocalDateTimeDeserializer;

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
        @JsonDeserialize(using = ShanghaiLocalDateTimeDeserializer.class)
        LocalDateTime eventTime,
        String detectionId
) {
}
