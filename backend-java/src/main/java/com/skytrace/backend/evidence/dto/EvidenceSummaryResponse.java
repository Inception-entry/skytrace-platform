package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceSummaryResponse(
        String evidenceCode,
        String originalFilename,
        String assetType,
        String sourceType,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String uploadedByName,
        long sizeBytes,
        Instant createdAt,
        boolean deleted
) {
}