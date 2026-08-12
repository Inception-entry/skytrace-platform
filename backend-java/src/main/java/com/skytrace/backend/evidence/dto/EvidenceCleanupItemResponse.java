package com.skytrace.backend.evidence.dto;

public record EvidenceCleanupItemResponse(
        String evidenceCode,
        String archiveBatchCode,
        String outcome,
        String message
) {
}
