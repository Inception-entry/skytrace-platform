package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9011";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String evidenceBucket = "skytrace-evidence";

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
}
