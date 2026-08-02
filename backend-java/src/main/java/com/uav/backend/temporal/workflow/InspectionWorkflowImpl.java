package com.uav.backend.temporal.workflow;

import com.uav.backend.temporal.activity.InspectionTaskActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class InspectionWorkflowImpl implements InspectionWorkflow {

    private final InspectionTaskActivities activities =
            Workflow.newActivityStub(
                    InspectionTaskActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(10))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(
                                                    Duration.ofSeconds(1))
                                            .setMaximumAttempts(5)
                                            .build()
                            )
                            .build()
            );

    private String status = "CREATED";
    private boolean finished;
    private String lastAlarmEventCode = "";

    @Override
    public void start(String taskCode) {
        activities.createTaskIfAbsent(taskCode);

        if (!finished) {
            status = "RUNNING";
            activities.updateStatus(taskCode, status);
        }

        Workflow.await(() -> finished);

        activities.updateStatus(taskCode, status);
    }

    @Override
    public void complete() {
        status = "COMPLETED";
        finished = true;
    }

    @Override
    public void cancel() {
        status = "CANCELLED";
        finished = true;
    }

    @Override
    public void alarmDetected(String eventCode) {
        if (eventCode != null && !eventCode.isBlank()) {
            lastAlarmEventCode = eventCode;
        }
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getLastAlarmEventCode() {
        return lastAlarmEventCode;
    }
}