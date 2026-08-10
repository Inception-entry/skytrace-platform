package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9011";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String evidenceBucket = "skytrace-evidence";
    private boolean publicReadEnabled = false;
    private int presignPreviewTtlSeconds = 300;
    private int presignDownloadTtlSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEvidenceBucket() {
        return evidenceBucket;
    }

    public void setEvidenceBucket(String evidenceBucket) {
        this.evidenceBucket = evidenceBucket;
    }

     public boolean isPublicReadEnabled() {
        return publicReadEnabled;
    }

    public void setPublicReadEnabled(boolean publicReadEnabled) {
        this.publicReadEnabled = publicReadEnabled;
    }

    public int getPresignPreviewTtlSeconds() {
        return presignPreviewTtlSeconds;
    }

    public void setPresignPreviewTtlSeconds(int presignPreviewTtlSeconds) {
        this.presignPreviewTtlSeconds = presignPreviewTtlSeconds;
    }

    public int getPresignDownloadTtlSeconds() {
        return presignDownloadTtlSeconds;
    }

    public void setPresignDownloadTtlSeconds(int presignDownloadTtlSeconds) {
        this.presignDownloadTtlSeconds = presignDownloadTtlSeconds;
    }
}
