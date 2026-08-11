package com.skytrace.backend.evidence.dto;

import java.util.List;

public record UpdateEvidenceMetadataRequest(
        String remark,
        String reviewStatus,
        String reviewComment,
        List<Long> tagIds
) {
}
