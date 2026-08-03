package com.skytrace.backend.ai.dto;

public record InspectionAnalysisResponse(
        String taskCode,
        String workflowId,
        String analysis
) {
}
