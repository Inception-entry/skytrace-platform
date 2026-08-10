package com.skytrace.backend.evidence.dto;

import java.util.List;

public record EvidencePageResponse(
        List<EvidenceSummaryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}