package com.skytrace.backend.evidence.dto;

public record EvidenceUploadResponse(
        String evidenceCode,
        String objectKey,
        String bucket,
        String contentType,
        long sizeBytes,
        String taskCode,
        String alarmEventCode,
        String publicPath
) {
}
