package com.skytrace.backend.evidence.dto;

import java.time.Instant;
import java.util.List;

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
        boolean deleted,
        String reviewStatus,
        String reviewComment,
        String remark,
        List<EvidenceTagResponse> tags,
        String reviewedByName,
        Instant reviewedAt,
        String analysisId,
        String derivativeStatus,
        String thumbnailObjectKey,
        String posterObjectKey,
        String thumbnailUrl,
        String posterUrl
) {
}
