package com.uav.backend.evidence.dto;

public record EvidenceUploadResponse(
        String objectKey,
        String bucket,
        String contentType,
        long sizeBytes,
        String taskCode,
        String alarmEventCode,
        String publicPath
) {
}
