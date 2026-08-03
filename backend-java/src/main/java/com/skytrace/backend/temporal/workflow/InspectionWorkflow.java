package com.skytrace.backend.temporal.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface InspectionWorkflow {

    @WorkflowMethod
    void start(String taskCode);

    @SignalMethod
    void complete();

    @SignalMethod
    void cancel();

    @SignalMethod
    void alarmDetected(String eventCode);

    @QueryMethod
    String getStatus();

    @QueryMethod
    String getLastAlarmEventCode();
}
