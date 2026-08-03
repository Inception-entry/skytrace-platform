package com.skytrace.backend.audit.dto;

public record AdminOverviewResponse(
        long totalTasks,
        long createdTasks,
        long runningTasks,
        long completedTasks,
        long cancelledTasks,
        long totalAnalyses,
        long totalAuditEvents,
        long failedAuditEventsLast24Hours
) {
}
