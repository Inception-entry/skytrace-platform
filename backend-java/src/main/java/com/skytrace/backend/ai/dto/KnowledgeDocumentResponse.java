package com.skytrace.backend.ai.dto;

public record KnowledgeDocumentResponse(
        String documentId,
        String filename,
        String contentType,
        int chunkCount,
        String uploadedAt) {
}
