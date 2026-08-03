package com.skytrace.backend.task.dto;

import java.time.LocalDateTime;

public record InspectionTaskResponse(
        String taskCode,
        String taskName,
        String deviceCode,
        String deviceName,
        String deviceStatus,
        String routeCode,
        String routeName,
        String status,
        LocalDateTime planStartTime,
        LocalDateTime planEndTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
