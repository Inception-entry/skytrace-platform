package com.skytrace.backend.evidence.dto;

import java.util.List;

public record BatchTagEvidenceRequest(
        List<String> evidenceCodes,
        List<Long> tagIds,
        boolean replace
) {
}
