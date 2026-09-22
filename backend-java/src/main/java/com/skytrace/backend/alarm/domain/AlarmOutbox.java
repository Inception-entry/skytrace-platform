package com.skytrace.backend.alarm.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alarm_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alarm_outbox_event_kind",
                columnNames = {"event_code", "kind"}
        )
)
public class AlarmOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, length = 64)
    private String eventCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlarmOutboxKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlarmOutboxStatus status = AlarmOutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected AlarmOutbox() {
    }

    public AlarmOutbox(String eventCode, AlarmOutboxKind kind, String payload) {
        this.eventCode = eventCode;
        this.kind = kind;
        this.payload = payload;
    }

    public void markSent() {
        this.status = AlarmOutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailedAttempt(int maxAttempts, LocalDateTime nextAttemptAt) {
        this.attempts = this.attempts + 1;
        if (this.attempts >= maxAttempts) {
            this.status = AlarmOutboxStatus.FAILED;
            return;
        }
        this.status = AlarmOutboxStatus.PENDING;
        this.availableAt = nextAttemptAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventCode() {
        return eventCode;
    }

    public AlarmOutboxKind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public AlarmOutboxStatus getStatus() {
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
