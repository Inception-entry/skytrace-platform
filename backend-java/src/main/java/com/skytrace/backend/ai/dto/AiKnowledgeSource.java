package com.skytrace.backend.ai.dto;

public record AiKnowledgeSource(
        String documentId,
        String filename,
        Integer page,
        double score
) {
}
