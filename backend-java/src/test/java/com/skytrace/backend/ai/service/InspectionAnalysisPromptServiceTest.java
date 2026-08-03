package com.skytrace.backend.ai.service;

import com.skytrace.backend.task.dto.InspectionTaskAnalysisContext;
import com.skytrace.backend.task.service.InspectionTaskService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InspectionAnalysisPromptServiceTest {

    @Test
    void shouldIncludeMysqlTaskContextInPrompt() {
        InspectionTaskService taskService =
                mock(InspectionTaskService.class);
        when(taskService.findAnalysisContext("TASK-REAL-001"))
                .thenReturn(new InspectionTaskAnalysisContext(
                        "TASK-REAL-001",
                        "东区输电线路巡检",
                        "UAV-001",
                        "RUNNING",
                        LocalDateTime.of(2026, 7, 17, 9, 30),
                        LocalDateTime.of(2026, 7, 17, 11, 30),
                        LocalDateTime.of(2026, 7, 16, 18, 0),
                        LocalDateTime.of(2026, 7, 17, 9, 35)
                ));
        InspectionAnalysisPromptService promptService =
                new InspectionAnalysisPromptService(taskService);

        String prompt = promptService.buildPrompt(
                "TASK-REAL-001",
                "是否可以继续飞行？"
        );

        assertThat(prompt)
                .contains("东区输电线路巡检")
                .contains("UAV-001")
                .contains("RUNNING")
                .contains("2026-07-17T09:30")
                .contains("是否可以继续飞行？");
    }
}
