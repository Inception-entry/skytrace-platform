package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9011";
    // 后端访问 MinIO 可使用容器内地址，浏览器签名链接必须使用外部可达地址。
    private String publicEndpoint = "http://localhost:9011";
    // 显式 Region 让 presign 无需访问公共域名探测存储区域。
    private String region = "us-east-1";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String evidenceBucket = "skytrace-evidence";
    private boolean publicReadEnabled = false;
    private int presignPreviewTtlSeconds = 300;
    private int presignDownloadTtlSeconds = 300;
    // SDK 默认连接超时长达五分钟；缩短后网络故障才能及时交给 Temporal 重试。
    private Duration connectTimeout = Duration.ofSeconds(5);
    // 大对象下载允许持续五分钟，避免把正常慢速归档误判为连接故障。
    private Duration readTimeout = Duration.ofMinutes(5);
    // 大 ZIP 上传与读取使用相同的宽松传输窗口。
    private Duration writeTimeout = Duration.ofMinutes(5);
    // 大归档包先写入磁盘临时目录，避免整个 ZIP 长时间占用 JVM 堆内存。
    private String archiveTempDir = Path.of(
            System.getProperty("java.io.tmpdir"),
            "skytrace-evidence-archives"
    ).toString();

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

    public String getPublicEndpoint() {
        return publicEndpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public String getArchiveTempDir() {
        return archiveTempDir;
    }

    public void setArchiveTempDir(String archiveTempDir) {
        this.archiveTempDir = archiveTempDir;
    }
}
