package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceDetailResponse(
        String evidenceCode,
        String objectKey,
        String bucket,
        String assetType,
        String sourceType,
        String contentType,
        String originalFilename,
        long sizeBytes,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String uploadedBy,
        String uploadedByName,
        Instant createdAt,
        boolean deleted
) {
}