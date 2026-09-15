package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skytrace.backend.alarm.repository.AlarmEventRepository;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceArchiveScopeType;
import com.skytrace.backend.evidence.dto.EvidenceArchiveJobResponse;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceArchiveServiceTest {

    private final EvidenceArchiveJobRepository repository =
            mock(EvidenceArchiveJobRepository.class);
    private EvidenceArchiveService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceArchiveService(
                repository,
                mock(EvidenceActorContextService.class),
                mock(EvidenceStorageService.class),
                mock(WorkflowClient.class),
                mock(InspectionTaskRepository.class),
                mock(AlarmEventRepository.class),
                "skytrace-inspection-task-queue"
        );
    }

    @Test
    void jobResponseInstantUsesShanghaiWallClockNotUtc() throws Exception {
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-20260824-ABCDEF");
        job.setScopeType(EvidenceArchiveScopeType.TASK);
        job.setScopeValue("TASK-001");
        job.setStatus(EvidenceArchiveJobStatus.COMPLETED);
        job.setCreatedAt(LocalDateTime.of(2026, 8, 24, 16, 0));
        job.setCompletedAt(LocalDateTime.of(2026, 8, 25, 0, 0));
        when(repository.findByJobCode("AR-20260824-ABCDEF"))
                .thenReturn(Optional.of(job));

        EvidenceArchiveJobResponse response = service.getJob("AR-20260824-ABCDEF");

        assertThat(response.createdAt())
                .isEqualTo(Instant.parse("2026-08-24T08:00:00Z"));
        assertThat(response.completedAt())
                .isEqualTo(Instant.parse("2026-08-24T16:00:00Z"));

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertThat(mapper.writeValueAsString(response.createdAt()))
                .isEqualTo("\"2026-08-24T08:00:00Z\"");
    }
}
