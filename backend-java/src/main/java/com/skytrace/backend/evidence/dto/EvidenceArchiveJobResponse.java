package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceArchiveJobResponse(
        String jobCode,
        String scopeType,
        String scopeValue,
        String status,
        String outputObjectKey,
        String manifestObjectKey,
        int totalFiles,
        long totalBytes,
        Instant createdAt,
        Instant completedAt,
        String errorMessage
) {
}
