package com.skytrace.backend.evidence.dto;

public record EvidenceMaintenancePolicyResponse(
        boolean hashBackfillEnabled,
        int hashBackfillBatchSize,
        int hashBackfillRetryHours,
        boolean cleanupEnabled,
        boolean cleanupDryRun,
        int cleanupRetentionDays,
        int cleanupBatchSize,
        String cleanupCron,
        String purgeEligibilityRule
) {
}
