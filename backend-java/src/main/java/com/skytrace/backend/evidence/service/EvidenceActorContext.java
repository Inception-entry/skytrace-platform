package com.skytrace.backend.evidence.service;

public record EvidenceActorContext(
        String actorId,
        String username,
        String roles,
        String requestId,
        String clientIp
) {
}