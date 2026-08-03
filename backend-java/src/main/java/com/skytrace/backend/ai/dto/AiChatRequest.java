package com.skytrace.backend.ai.dto;

public record AiChatRequest(
        String sessionId,
        String message,
        String knowledgeQuery) {
}
