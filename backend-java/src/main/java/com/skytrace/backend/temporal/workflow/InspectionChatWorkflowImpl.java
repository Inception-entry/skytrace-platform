package com.skytrace.backend.temporal.workflow;

import com.skytrace.backend.temporal.activity.InspectionAnalysisActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class InspectionChatWorkflowImpl
        implements InspectionChatWorkflow {

    private final InspectionAnalysisActivities activities =
            Workflow.newActivityStub(
                    InspectionAnalysisActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(
                                    Duration.ofSeconds(270))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(
                                                    Duration.ofSeconds(2))
                                            .setMaximumAttempts(3)
                                            .build()
                            )
                            .build()
            );

    @Override
    public String chat(
            String taskCode,
            String sessionId,
            String question) {
        return activities.chatTask(
                taskCode,
                sessionId,
                question,
                Workflow.getInfo().getWorkflowId()
        );
    }
}
