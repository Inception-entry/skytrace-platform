package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceAccessUrlResponse(
        String url,
        Instant expiresAt
) {
}