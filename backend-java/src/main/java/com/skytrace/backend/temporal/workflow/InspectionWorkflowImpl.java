package com.skytrace.backend.temporal.workflow;

import com.skytrace.backend.temporal.activity.InspectionTaskActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * 巡检任务工作流：CREATED → RUNNING → COMPLETED|CANCELLED|TIMED_OUT。
 * <p>
 * 相对初版加厚点：启动前校验、信号幂等、最长运行超时、可查询开始时间与结束原因。
 * 仍不做飞控编排；深度 Activity（证据归档、通知）见 docs/temporal-integration.md。
 */
public class InspectionWorkflowImpl implements InspectionWorkflow {

    /** 演示/联调默认最长运行时间；生产可再按任务计划时长拆 Activity 读取。 */
    private static final Duration MAX_RUN_DURATION = Duration.ofHours(24);

    private final InspectionTaskActivities activities =
            Workflow.newActivityStub(
                    InspectionTaskActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(15))
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
    private long startedAtEpochMs;
    private String finishReason = "";

    @Override
    public void start(String taskCode) {
        activities.createTaskIfAbsent(taskCode);
        activities.assertStartable(taskCode);

        if (finished) {
            return;
        }

        status = "RUNNING";
        startedAtEpochMs = Workflow.currentTimeMillis();
        activities.updateStatus(taskCode, status);

        boolean completedInTime = Workflow.await(MAX_RUN_DURATION, () -> finished);
        if (!completedInTime && !finished) {
            status = "TIMED_OUT";
            finishReason = "max_run_duration_exceeded";
            finished = true;
        }

        activities.updateStatus(taskCode, status);
    }

    @Override
    public void complete() {
        if (finished) {
            return;
        }
        status = "COMPLETED";
        finishReason = "signal_complete";
        finished = true;
    }

    @Override
    public void cancel() {
        if (finished) {
            return;
        }
        status = "CANCELLED";
        finishReason = "signal_cancel";
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

    @Override
    public long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    @Override
    public String getFinishReason() {
        return finishReason;
    }
}
