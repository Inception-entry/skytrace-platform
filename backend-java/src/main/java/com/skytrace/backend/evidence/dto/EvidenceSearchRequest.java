package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceSearchRequest(
        Integer page,
        Integer size,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String assetType,
        String sourceType,
        String reviewStatus,
        Instant startTime,
        Instant endTime,
        String keyword,
        Boolean includeDeleted
) {
}
