package com.skytrace.backend.evidence.dto;

import java.time.LocalDateTime;

public record EvidenceAssetResponse(
        String objectKey,
        String bucket,
        String contentType,
        long sizeBytes,
        String originalFilename,
        String taskCode,
        String alarmEventCode,
        String publicPath,
        LocalDateTime createdAt
) {
}
