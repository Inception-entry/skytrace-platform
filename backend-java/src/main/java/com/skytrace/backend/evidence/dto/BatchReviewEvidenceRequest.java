package com.skytrace.backend.evidence.dto;

import java.util.List;

public record BatchReviewEvidenceRequest(
        List<String> evidenceCodes,
        String reviewStatus,
        String reviewComment
) {
}
