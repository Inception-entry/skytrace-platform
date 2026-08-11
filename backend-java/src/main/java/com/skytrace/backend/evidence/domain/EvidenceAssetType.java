package com.skytrace.backend.evidence.domain;

public enum EvidenceAssetType {
    IMAGE,
    VIDEO;

    public static EvidenceAssetType fromContentType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return VIDEO;
        }
        return IMAGE;
    }
}
