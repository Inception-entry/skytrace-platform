package com.skytrace.backend.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionResolverTest {

    private final AuditActionResolver resolver =
            new AuditActionResolver();

    @Test
    void resolvesTaskAndWorkflowActionsWithoutRequestBody() {
        AuditActionResolver.AuditDescriptor update = resolver.resolve(
                request("PUT", "/api/inspection-tasks/TASK-008")
        );
        AuditActionResolver.AuditDescriptor stream = resolver.resolve(
                request(
                        "POST",
                        "/api/inspection-workflows/TASK-008/analysis/stream"
                )
        );

        assertThat(update.action()).isEqualTo("TASK_UPDATE");
        assertThat(update.resourceType())
                .isEqualTo("INSPECTION_TASK");
        assertThat(update.resourceId()).isEqualTo("TASK-008");
        assertThat(stream.action())
                .isEqualTo("AI_ANALYSIS_STREAM");
        assertThat(stream.resourceId()).isEqualTo("TASK-008");
    }

    @Test
    void doesNotAuditReadsOrKnowledgeSearch() {
        assertThat(resolver.shouldAudit(
                request("GET", "/api/inspection-tasks")
        )).isFalse();
        assertThat(resolver.shouldAudit(
                request("POST", "/api/knowledge/search")
        )).isFalse();
        assertThat(resolver.shouldAudit(
                request("POST", "/api/knowledge/documents")
        )).isTrue();
    }

    @Test
    void resolvesEvidenceArchiveActions() {
        AuditActionResolver.AuditDescriptor create = resolver.resolve(
                request("POST", "/api/evidence/archive-jobs")
        );
        AuditActionResolver.AuditDescriptor download = resolver.resolve(
                request(
                        "POST",
                        "/api/evidence/archive-jobs/AR-20260811-ABC123/download-url"
                )
        );
        AuditActionResolver.AuditDescriptor manifest = resolver.resolve(
                request(
                        "POST",
                        "/api/evidence/archive-jobs/AR-20260811-ABC123/manifest-url"
                )
        );

        assertThat(create.action()).isEqualTo("EVIDENCE_ARCHIVE_JOB_CREATE");
        assertThat(create.resourceType()).isEqualTo("EVIDENCE_ARCHIVE_JOB");
        assertThat(download.action())
                .isEqualTo("EVIDENCE_ARCHIVE_DOWNLOAD_URL");
        assertThat(download.resourceId())
                .isEqualTo("AR-20260811-ABC123");
        assertThat(manifest.action())
                .isEqualTo("EVIDENCE_ARCHIVE_MANIFEST_URL");
    }

    @Test
    void resolvesEvidenceMaintenanceActions() {
        // 管理员手动回填和清理都必须进入 HTTP 审计链路。
        AuditActionResolver.AuditDescriptor backfill = resolver.resolve(
                request(
                        "POST",
                        "/api/admin/evidence-maintenance/hash-backfill"
                )
        );
        AuditActionResolver.AuditDescriptor cleanup = resolver.resolve(
                request(
                        "POST",
                        "/api/admin/evidence-maintenance/cleanup"
                )
        );

        assertThat(backfill.action()).isEqualTo("EVIDENCE_HASH_BACKFILL");
        assertThat(cleanup.action()).isEqualTo("EVIDENCE_CLEANUP_EXECUTE");
        assertThat(cleanup.resourceType())
                .isEqualTo("EVIDENCE_MAINTENANCE");
    }

    private MockHttpServletRequest request(
            String method,
            String uri) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        return request;
    }
}
