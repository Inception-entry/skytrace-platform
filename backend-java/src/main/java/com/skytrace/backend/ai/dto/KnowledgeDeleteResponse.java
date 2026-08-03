package com.skytrace.backend.ai.dto;

public record KnowledgeDeleteResponse(
        String documentId,
        int deletedChunks) {
}
