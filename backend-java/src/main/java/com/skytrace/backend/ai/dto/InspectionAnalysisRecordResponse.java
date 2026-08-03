package com.skytrace.backend.ai.dto;

import com.skytrace.backend.ai.domain.AnalysisChannel;

import java.time.LocalDateTime;
import java.util.List;

public record InspectionAnalysisRecordResponse(
        String analysisId,
        String taskCode,
        String sessionId,
        AnalysisChannel channel,
        String question,
        String answer,
        String model,
        List<AiKnowledgeSource> sources,
        LocalDateTime createdAt
) {
}
