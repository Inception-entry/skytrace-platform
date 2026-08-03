package com.skytrace.backend.audit.service;

import com.skytrace.backend.ai.repository.InspectionAnalysisRecordRepository;
import com.skytrace.backend.audit.domain.AuditLog;
import com.skytrace.backend.audit.dto.AdminOverviewResponse;
import com.skytrace.backend.audit.dto.AuditLogPageResponse;
import com.skytrace.backend.audit.dto.AuditLogResponse;
import com.skytrace.backend.audit.repository.AuditLogRepository;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditRepository;
    private final InspectionTaskRepository taskRepository;
    private final InspectionAnalysisRecordRepository analysisRepository;

    public AuditLogService(
            AuditLogRepository auditRepository,
            InspectionTaskRepository taskRepository,
            InspectionAnalysisRecordRepository analysisRepository) {
        this.auditRepository = auditRepository;
        this.taskRepository = taskRepository;
        this.analysisRepository = analysisRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog auditLog) {
        auditRepository.save(auditLog);
    }

    public AuditLogPageResponse search(
            int requestedPage,
            int requestedSize,
            String action,
            String outcome,
            String username) {
        int page = Math.max(requestedPage, 0);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        Page<AuditLog> result = auditRepository.search(
                normalizeCode(action),
                normalizeCode(outcome),
                normalizeText(username),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );
        return new AuditLogPageResponse(
                result.getContent().stream()
                        .map(this::toResponse)
                        .toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public AdminOverviewResponse overview() {
        return new AdminOverviewResponse(
                taskRepository.count(),
                taskRepository.countByStatus("CREATED"),
                taskRepository.countByStatus("RUNNING"),
                taskRepository.countByStatus("COMPLETED"),
                taskRepository.countByStatus("CANCELLED"),
                analysisRepository.count(),
                auditRepository.count(),
                auditRepository.countByOutcomeAndCreatedAtAfter(
                        "FAILURE",
                        LocalDateTime.now().minusHours(24)
                )
        );
    }

    private AuditLogResponse toResponse(AuditLog audit) {
        return new AuditLogResponse(
                audit.getId(),
                audit.getRequestId(),
                audit.getActorId(),
                audit.getUsername(),
                audit.getRoles(),
                audit.getAction(),
                audit.getResourceType(),
                audit.getResourceId(),
                audit.getHttpMethod(),
                audit.getRequestPath(),
                audit.getStatusCode(),
                audit.getOutcome(),
                audit.getClientIp(),
                audit.getDurationMs(),
                audit.getErrorType(),
                audit.getCreatedAt()
        );
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
