package com.skytrace.backend.evidence.dto;

import java.time.Instant;
import java.util.List;

public record EvidenceCleanupBatchResponse(
        boolean dryRun,
        int retentionDays,
        Instant cutoff,
        long eligibleCount,
        int selected,
        int claimed,
        int purged,
        int failed,
        int staleClaimsReleased,
        List<EvidenceCleanupItemResponse> items
) {
}
