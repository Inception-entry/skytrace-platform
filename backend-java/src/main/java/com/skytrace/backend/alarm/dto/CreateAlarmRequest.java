package com.skytrace.backend.alarm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateAlarmRequest(
        @NotBlank String deviceCode,
        String taskCode,
        @NotBlank String eventType,
        String weaponType,
        BigDecimal confidence,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        String videoUrl,
        @NotNull LocalDateTime eventTime
) {}
