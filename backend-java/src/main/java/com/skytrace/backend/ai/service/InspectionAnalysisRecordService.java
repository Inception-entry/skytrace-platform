package com.skytrace.backend.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.ai.domain.AnalysisChannel;
import com.skytrace.backend.ai.domain.InspectionAnalysisRecord;
import com.skytrace.backend.ai.dto.AiKnowledgeSource;
import com.skytrace.backend.ai.dto.InspectionAnalysisRecordResponse;
import com.skytrace.backend.ai.repository.InspectionAnalysisRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InspectionAnalysisRecordService {

    private static final TypeReference<List<AiKnowledgeSource>>
            SOURCE_LIST_TYPE = new TypeReference<>() {
            };

    private final InspectionAnalysisRecordRepository repository;
    private final ObjectMapper objectMapper;

    public InspectionAnalysisRecordService(
            InspectionAnalysisRecordRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<String> findCompletedAnswer(String analysisId) {
        return repository.findByAnalysisId(analysisId)
                .map(InspectionAnalysisRecord::getAnswer);
    }

    public List<InspectionAnalysisRecordResponse> findByTaskCode(
            String taskCode) {
        return repository.findByTaskCodeOrderByCreatedAtDesc(taskCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InspectionAnalysisRecordResponse saveCompleted(
            String analysisId,
            String taskCode,
            String sessionId,
            AnalysisChannel channel,
            String question,
            String answer,
            String model,
            List<AiKnowledgeSource> sources) {
        Optional<InspectionAnalysisRecord> existing =
                repository.findByAnalysisId(analysisId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        InspectionAnalysisRecord record =
                new InspectionAnalysisRecord(
                        analysisId,
                        taskCode,
                        sessionId,
                        channel,
                        question,
                        answer,
                        model,
                        serializeSources(sources)
                );
        return toResponse(repository.save(record));
    }

    private InspectionAnalysisRecordResponse toResponse(
            InspectionAnalysisRecord record) {
        return new InspectionAnalysisRecordResponse(
                record.getAnalysisId(),
                record.getTaskCode(),
                record.getSessionId(),
                record.getChannel(),
                record.getQuestion(),
                record.getAnswer(),
                record.getModel(),
                deserializeSources(record.getSourcesJson()),
                record.getCreatedAt()
        );
    }

    private String serializeSources(List<AiKnowledgeSource> sources) {
        try {
            return objectMapper.writeValueAsString(
                    sources == null ? List.of() : sources
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "无法序列化 AI 知识来源",
                    exception
            );
        }
    }

    private List<AiKnowledgeSource> deserializeSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SOURCE_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "无法解析 AI 知识来源",
                    exception
            );
        }
    }
}
