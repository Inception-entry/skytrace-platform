package com.skytrace.backend.ai.domain;

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
@Table(name = "ai_analysis_result")
public class InspectionAnalysisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "analysis_id",
            nullable = false,
            unique = true,
            length = 128
    )
    private String analysisId;

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisChannel channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String answer;

    @Column(length = 128)
    private String model;

    @Column(name = "sources_json", columnDefinition = "LONGTEXT")
    private String sourcesJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InspectionAnalysisRecord() {
    }

    public InspectionAnalysisRecord(
            String analysisId,
            String taskCode,
            String sessionId,
            AnalysisChannel channel,
            String question,
            String answer,
            String model,
            String sourcesJson) {
        this.analysisId = analysisId;
        this.taskCode = taskCode;
        this.sessionId = sessionId;
        this.channel = channel;
        this.question = question;
        this.answer = answer;
        this.model = model;
        this.sourcesJson = sourcesJson;
        this.createdAt = LocalDateTime.now();
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public String getSessionId() {
        return sessionId;
    }

    public AnalysisChannel getChannel() {
        return channel;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModel() {
        return model;
    }

    public String getSourcesJson() {
        return sourcesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
