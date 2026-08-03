package com.skytrace.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectionAnalysisRequest(
        @Size(max = 64, message = "会话编号不能超过 64 个字符")
        String sessionId,

        @NotBlank(message = "分析问题不能为空")
        @Size(max = 2000, message = "分析问题不能超过 2000 个字符")
        String question
) {
}
