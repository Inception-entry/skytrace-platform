package com.skytrace.backend.evidence.dto;

import java.time.Instant;
import java.util.List;

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
        boolean deleted,
        String reviewStatus,
        String contentHash,
        String archiveStatus,
        List<EvidenceTagResponse> tags,
        String thumbnailUrl,
        String posterUrl
) {
}
