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
}
