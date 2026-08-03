package com.skytrace.backend.device.dto;

import java.time.LocalDateTime;

public record DeviceResponse(
        String deviceCode,
        String deviceName,
        String deviceType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
