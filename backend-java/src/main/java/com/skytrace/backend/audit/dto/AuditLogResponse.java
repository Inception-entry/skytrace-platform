package com.skytrace.backend.audit.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        long id,
        String requestId,
        String actorId,
        String username,
        String roles,
        String action,
        String resourceType,
        String resourceId,
        String httpMethod,
        String requestPath,
        int statusCode,
        String outcome,
        String clientIp,
        long durationMs,
        String errorType,
        LocalDateTime createdAt
) {
}
