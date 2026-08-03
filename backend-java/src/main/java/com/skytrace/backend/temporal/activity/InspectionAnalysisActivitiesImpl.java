package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.ai.client.AiChatClient;
import com.skytrace.backend.ai.client.AiClientException;
import com.skytrace.backend.ai.domain.AnalysisChannel;
import com.skytrace.backend.ai.dto.AiChatResponse;
import com.skytrace.backend.ai.service.InspectionAnalysisPromptService;
import com.skytrace.backend.ai.service.InspectionAnalysisRecordService;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

@Component("inspectionAnalysisActivities")
public class InspectionAnalysisActivitiesImpl
        implements InspectionAnalysisActivities {

    private final AiChatClient aiChatClient;
    private final InspectionAnalysisPromptService promptService;
    private final InspectionAnalysisRecordService recordService;

    public InspectionAnalysisActivitiesImpl(
            AiChatClient aiChatClient,
            InspectionAnalysisPromptService promptService,
            InspectionAnalysisRecordService recordService) {
        this.aiChatClient = aiChatClient;
        this.promptService = promptService;
        this.recordService = recordService;
    }

    @Override
    public String analyzeTask(
            String taskCode,
            String question,
            String analysisId) {
        return chatTask(
                taskCode,
                analysisId,
                question,
                analysisId
        );
    }

    @Override
    public String chatTask(
            String taskCode,
            String sessionId,
            String question,
            String analysisId) {
        var existingAnswer =
                recordService.findCompletedAnswer(analysisId);
        if (existingAnswer.isPresent()) {
            return existingAnswer.get();
        }

        String prompt = promptService.buildPrompt(taskCode, question);
        AiChatResponse response;
        try {
            response = aiChatClient.chatResponse(
                    sessionId,
                    prompt,
                    question
            );
        } catch (AiClientException exception) {
            throw temporalFailure(exception);
        }

        return recordService.saveCompleted(
                analysisId,
                taskCode,
                sessionId,
                AnalysisChannel.TEMPORAL,
                question,
                response.answer(),
                response.model(),
                response.sources()
        ).answer();
    }

    private ApplicationFailure temporalFailure(
            AiClientException exception) {
        if (exception.retryable()) {
            return ApplicationFailure.newFailureWithCause(
                    exception.getMessage(),
                    exception.errorCode().name(),
                    exception
            );
        }
        return ApplicationFailure.newNonRetryableFailureWithCause(
                exception.getMessage(),
                exception.errorCode().name(),
                exception
        );
    }
}
