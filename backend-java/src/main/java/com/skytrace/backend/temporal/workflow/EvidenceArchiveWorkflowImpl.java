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
                            // Worker 无心跳超过十分钟时，Temporal 可以把 Activity 判为失联并重试。
                            .setHeartbeatTimeout(Duration.ofMinutes(10))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            // 指数退避最多等待 30 秒，覆盖短时 MinIO/网络抖动。
                                            .setMaximumInterval(Duration.ofSeconds(30))
                                            // 有限重试避免永久占用 Workflow，同时提供约两分钟恢复窗口。
                                            .setMaximumAttempts(8)
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
