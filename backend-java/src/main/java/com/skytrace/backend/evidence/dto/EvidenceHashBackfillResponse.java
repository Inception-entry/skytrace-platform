package com.skytrace.backend.evidence.dto;

import java.util.List;

public record EvidenceHashBackfillResponse(
        int selected,
        int claimed,
        int succeeded,
        int failed,
        List<String> failedEvidenceCodes
) {
}
