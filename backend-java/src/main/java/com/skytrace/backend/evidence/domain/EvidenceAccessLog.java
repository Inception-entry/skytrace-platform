package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_access_log")
public class EvidenceAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "evidence_code", nullable = false, length = 64)
    private String evidenceCode;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 256)
    private String roles;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public EvidenceAccessLog() {
    }

    public EvidenceAccessLog(
            Long evidenceId,
            String evidenceCode,
            String action,
            String actorId,
            String username,
            String roles,
            String requestId,
            String clientIp) {
        this.evidenceId = evidenceId;
        this.evidenceCode = evidenceCode;
        this.action = action;
        this.actorId = actorId;
        this.username = username;
        this.roles = roles;
        this.requestId = requestId;
        this.clientIp = clientIp;
    }

    public Long getId() {
        return id;
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public String getEvidenceCode() {
        return evidenceCode;
    }

    public String getAction() {
        return action;
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

    public String getRequestId() {
        return requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}