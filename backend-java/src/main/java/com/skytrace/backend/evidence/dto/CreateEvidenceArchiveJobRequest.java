package com.skytrace.backend.evidence.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateEvidenceArchiveJobRequest(
        @NotBlank(message = "scopeType 不能为空")
        String scopeType,
        @NotBlank(message = "scopeValue 不能为空")
        String scopeValue
) {
}
