package com.skytrace.backend.evidence.dto;

public record EvidenceMinioDeletePayload(String bucket, String objectKey) {
}
