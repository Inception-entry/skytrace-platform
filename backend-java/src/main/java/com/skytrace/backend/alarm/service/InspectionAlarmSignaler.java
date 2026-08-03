package com.skytrace.backend.alarm.service;

import com.skytrace.backend.temporal.workflow.InspectionWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InspectionAlarmSignaler {

    private static final Logger log = LoggerFactory.getLogger(
            InspectionAlarmSignaler.class
    );

    private final WorkflowClient workflowClient;

    public InspectionAlarmSignaler(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public void signalAlarmDetected(String taskCode, String eventCode) {
        if (taskCode == null || taskCode.isBlank()) {
            return;
        }
        try {
            InspectionWorkflow workflow = workflowClient.newWorkflowStub(
                    InspectionWorkflow.class,
                    "inspection-" + taskCode
            );
            workflow.alarmDetected(eventCode);
            log.info(
                    "event=alarm_workflow_signaled taskCode={} eventCode={}",
                    taskCode,
                    eventCode
            );
        } catch (WorkflowNotFoundException exception) {
            log.info(
                    "event=alarm_workflow_missing taskCode={} eventCode={}",
                    taskCode,
                    eventCode
            );
        } catch (Exception exception) {
            log.warn(
                    "event=alarm_workflow_signal_failed taskCode={} eventCode={} reason={}",
                    taskCode,
                    eventCode,
                    exception.getMessage()
            );
        }
    }
}
