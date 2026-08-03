package com.skytrace.backend.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface InspectionChatWorkflow {

    @WorkflowMethod
    String chat(String taskCode, String sessionId, String question);
}
