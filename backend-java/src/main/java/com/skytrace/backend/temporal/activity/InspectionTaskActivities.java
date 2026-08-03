package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InspectionTaskActivities {

    @ActivityMethod
    void createTaskIfAbsent(String taskCode);

    @ActivityMethod
    void updateStatus(String taskCode, String status);
}