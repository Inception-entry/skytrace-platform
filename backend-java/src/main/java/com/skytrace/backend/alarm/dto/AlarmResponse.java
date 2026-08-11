package com.skytrace.backend.alarm.dto;

import com.skytrace.backend.alarm.domain.AlarmStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlarmResponse(
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
        String primaryEvidenceCode,
        String primaryVideoEvidenceCode,
        AlarmStatus status,
        LocalDateTime eventTime
) {}
