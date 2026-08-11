package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_archive_job")
public class EvidenceArchiveJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_code", nullable = false, unique = true, length = 64)
    private String jobCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private EvidenceArchiveScopeType scopeType;

    @Column(name = "scope_value", nullable = false, length = 128)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvidenceArchiveJobStatus status = EvidenceArchiveJobStatus.PENDING;

    @Column(name = "output_bucket", length = 128)
    private String outputBucket;

    @Column(name = "output_object_key", length = 512)
    private String outputObjectKey;

    @Column(name = "manifest_object_key", length = 512)
    private String manifestObjectKey;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_by_name", nullable = false, length = 128)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public EvidenceArchiveScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(EvidenceArchiveScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public void setScopeValue(String scopeValue) {
        this.scopeValue = scopeValue;
    }

    public EvidenceArchiveJobStatus getStatus() {
        return status;
    }

    public void setStatus(EvidenceArchiveJobStatus status) {
        this.status = status;
    }

    public String getOutputBucket() {
        return outputBucket;
    }

    public void setOutputBucket(String outputBucket) {
        this.outputBucket = outputBucket;
    }

    public String getOutputObjectKey() {
        return outputObjectKey;
    }

    public void setOutputObjectKey(String outputObjectKey) {
        this.outputObjectKey = outputObjectKey;
    }

    public String getManifestObjectKey() {
        return manifestObjectKey;
    }

    public void setManifestObjectKey(String manifestObjectKey) {
        this.manifestObjectKey = manifestObjectKey;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
