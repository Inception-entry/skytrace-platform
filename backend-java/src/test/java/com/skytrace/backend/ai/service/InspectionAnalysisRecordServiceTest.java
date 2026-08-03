package com.skytrace.backend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.ai.domain.AnalysisChannel;
import com.skytrace.backend.ai.domain.InspectionAnalysisRecord;
import com.skytrace.backend.ai.dto.AiKnowledgeSource;
import com.skytrace.backend.ai.repository.InspectionAnalysisRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionAnalysisRecordServiceTest {

    @Test
    void savesCompletedAnalysisAndSerializesSources() {
        InspectionAnalysisRecordRepository repository =
                mock(InspectionAnalysisRecordRepository.class);
        when(repository.findByAnalysisId("analysis-001"))
                .thenReturn(Optional.empty());
        when(repository.save(any(InspectionAnalysisRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InspectionAnalysisRecordService service =
                new InspectionAnalysisRecordService(
                        repository,
                        new ObjectMapper()
                );

        var response = service.saveCompleted(
                "analysis-001",
                "TASK-001",
                "session-001",
                AnalysisChannel.STREAM,
                "任务安全吗？",
                "可以继续执行",
                "skytrace-model",
                List.of(new AiKnowledgeSource(
                        "doc-1",
                        "manual.pdf",
                        2,
                        0.95
                ))
        );

        assertThat(response.answer()).isEqualTo("可以继续执行");
        assertThat(response.sources())
                .singleElement()
                .extracting(AiKnowledgeSource::filename)
                .isEqualTo("manual.pdf");
        verify(repository).save(any(InspectionAnalysisRecord.class));
    }

    @Test
    void doesNotInsertDuplicateAnalysisId() {
        InspectionAnalysisRecordRepository repository =
                mock(InspectionAnalysisRecordRepository.class);
        InspectionAnalysisRecord existing =
                new InspectionAnalysisRecord(
                        "analysis-001",
                        "TASK-001",
                        "session-001",
                        AnalysisChannel.TEMPORAL,
                        "问题",
                        "已有答案",
                        "skytrace-model",
                        "[]"
                );
        when(repository.findByAnalysisId("analysis-001"))
                .thenReturn(Optional.of(existing));
        InspectionAnalysisRecordService service =
                new InspectionAnalysisRecordService(
                        repository,
                        new ObjectMapper()
                );

        var response = service.saveCompleted(
                "analysis-001",
                "TASK-001",
                "session-001",
                AnalysisChannel.TEMPORAL,
                "新问题",
                "新答案",
                "skytrace-model",
                List.of()
        );

        assertThat(response.answer()).isEqualTo("已有答案");
        verify(repository, never()).save(any());
    }
}
