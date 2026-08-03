package com.skytrace.backend.ai.dto;

import java.util.List;

public record AiStreamResult(
        String model,
        String answer,
        List<AiKnowledgeSource> sources
) {
    public AiStreamResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
