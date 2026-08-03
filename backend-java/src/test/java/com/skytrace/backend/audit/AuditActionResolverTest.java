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

    private MockHttpServletRequest request(
            String method,
            String uri) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        return request;
    }
}
