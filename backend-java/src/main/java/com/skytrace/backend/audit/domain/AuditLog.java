package com.skytrace.backend.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 256)
    private String roles;

    @Column(name = "action_code", nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 512)
    private String requestPath;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "client_ip", nullable = false, length = 64)
    private String clientIp;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_type", length = 128)
    private String errorType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            String requestId,
            String actorId,
            String username,
            String roles,
            String action,
            String resourceType,
            String resourceId,
            String httpMethod,
            String requestPath,
            int statusCode,
            String outcome,
            String clientIp,
            long durationMs,
            String errorType) {
        this.requestId = requestId;
        this.actorId = actorId;
        this.username = username;
        this.roles = roles;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
        this.outcome = outcome;
        this.clientIp = clientIp;
        this.durationMs = durationMs;
        this.errorType = errorType;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoles() {
        return roles;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getClientIp() {
        return clientIp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getErrorType() {
        return errorType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
