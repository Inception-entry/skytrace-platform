package com.skytrace.backend.audit.dto;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
