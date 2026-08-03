package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InspectionAnalysisActivities {

    @ActivityMethod
    String analyzeTask(
            String taskCode,
            String question,
            String analysisId
    );

    @ActivityMethod
    String chatTask(
            String taskCode,
            String sessionId,
            String question,
            String analysisId
    );
}
