package com.skytrace.backend.evidence.service;

import com.skytrace.backend.audit.domain.AuditLog;
import com.skytrace.backend.audit.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EvidenceMaintenanceAuditService {

    private static final String INTERNAL_PATH =
            "/internal/evidence-maintenance/cleanup";

    private final AuditLogService auditLogService;

    public EvidenceMaintenanceAuditService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void recordPurge(
            String evidenceCode,
            String outcome,
            int statusCode,
            Throwable error) {
        // REQUIRES_NEW 审计事务独立提交，业务事务失败也不会抹掉删除意图。
        auditLogService.record(new AuditLog(
                UUID.randomUUID().toString(),
                "system:evidence-maintenance",
                "evidence-maintenance",
                "SYSTEM",
                "EVIDENCE_PHYSICAL_PURGE",
                "EVIDENCE",
                evidenceCode,
                "INTERNAL",
                INTERNAL_PATH,
                statusCode,
                outcome,
                "internal",
                0L,
                error == null ? null : error.getClass().getSimpleName()
        ));
    }
}
