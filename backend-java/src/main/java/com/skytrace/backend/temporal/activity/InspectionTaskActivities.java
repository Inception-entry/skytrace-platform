package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InspectionTaskActivities {

    @ActivityMethod
    void createTaskIfAbsent(String taskCode);

    /**
     * 启动前校验：任务必须存在且非终态，避免对已完成任务重复推进。
     */
    @ActivityMethod
    void assertStartable(String taskCode);

    @ActivityMethod
    void updateStatus(String taskCode, String status);

    /**
     * 本次运行最多再等待的毫秒数，按计划结束时间夹在 1 分钟到 24 小时之间。
     */
    @ActivityMethod
    long plannedTimeoutMillis(String taskCode);
}
