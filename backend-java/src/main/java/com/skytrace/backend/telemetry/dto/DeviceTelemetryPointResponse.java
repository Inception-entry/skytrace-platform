package com.skytrace.backend.telemetry.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeviceTelemetryPointResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,
        BigDecimal heading,
        LocalDateTime recordedAt
) {
}
