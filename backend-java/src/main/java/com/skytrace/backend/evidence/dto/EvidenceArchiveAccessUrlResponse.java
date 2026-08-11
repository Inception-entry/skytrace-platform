package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceArchiveAccessUrlResponse(
        String url,
        Instant expiresAt
) {
}
