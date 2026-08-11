package com.skytrace.backend.temporal.workflow;

import com.skytrace.backend.temporal.activity.EvidenceArchiveActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class EvidenceArchiveWorkflowImpl implements EvidenceArchiveWorkflow {

    private final EvidenceArchiveActivities activities =
            Workflow.newActivityStub(
                    EvidenceArchiveActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(30))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            .setMaximumAttempts(3)
                                            .build()
                            )
                            .build()
            );

    @Override
    public void archive(String jobCode) {
        activities.markRunning(jobCode);
        try {
            activities.executeArchive(jobCode);
        } catch (RuntimeException exception) {
            activities.markFailed(jobCode, exception.getMessage());
            throw exception;
        }
    }
}
