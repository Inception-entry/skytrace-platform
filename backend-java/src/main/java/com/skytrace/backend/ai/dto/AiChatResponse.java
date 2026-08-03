package com.skytrace.backend.ai.dto;

import java.util.List;

public record AiChatResponse(
        String model,
        String answer,
        List<AiKnowledgeSource> sources
) {
    public AiChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
