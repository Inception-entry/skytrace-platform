package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface EvidenceArchiveActivities {
    @ActivityMethod
    void markRunning(String jobCode);

    @ActivityMethod
    void executeArchive(String jobCode);

    @ActivityMethod
    void markFailed(String jobCode, String errorMessage);
}
