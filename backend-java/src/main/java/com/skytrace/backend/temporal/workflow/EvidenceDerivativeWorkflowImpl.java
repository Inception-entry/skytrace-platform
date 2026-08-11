package com.skytrace.backend.temporal.workflow;

import com.skytrace.backend.temporal.activity.EvidenceDerivativeActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class EvidenceDerivativeWorkflowImpl implements EvidenceDerivativeWorkflow {

    private final EvidenceDerivativeActivities activities =
            Workflow.newActivityStub(
                    EvidenceDerivativeActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            .setMaximumAttempts(3)
                                            .build()
                            )
                            .build()
            );

    @Override
    public void enrich(String evidenceCode) {
        activities.generateDerivatives(evidenceCode);
    }
}
