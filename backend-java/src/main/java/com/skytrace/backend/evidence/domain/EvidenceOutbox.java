package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "evidence_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evidence_outbox_aggregate_kind",
                columnNames = {"aggregate_code", "kind"}
        )
)
public class EvidenceOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_code", nullable = false, length = 512)
    private String aggregateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvidenceOutboxKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvidenceOutboxStatus status = EvidenceOutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected EvidenceOutbox() {
    }

    public EvidenceOutbox(String aggregateCode, EvidenceOutboxKind kind, String payload) {
        this.aggregateCode = aggregateCode;
        this.kind = kind;
        this.payload = payload;
    }

    public void markSent() {
        this.status = EvidenceOutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailedAttempt(int maxAttempts, LocalDateTime nextAttemptAt) {
        this.attempts = this.attempts + 1;
        if (this.attempts >= maxAttempts) {
            this.status = EvidenceOutboxStatus.FAILED;
            return;
        }
        this.status = EvidenceOutboxStatus.PENDING;
        this.availableAt = nextAttemptAt;
    }

    public Long getId() {
        return id;
    }

    public String getAggregateCode() {
        return aggregateCode;
    }

    public EvidenceOutboxKind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public EvidenceOutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
