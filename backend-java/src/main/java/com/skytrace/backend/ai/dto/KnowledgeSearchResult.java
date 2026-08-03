package com.skytrace.backend.ai.dto;

public record KnowledgeSearchResult(
        String documentId,
        String filename,
        String content,
        Integer page,
        int chunkIndex,
        double score) {
}
