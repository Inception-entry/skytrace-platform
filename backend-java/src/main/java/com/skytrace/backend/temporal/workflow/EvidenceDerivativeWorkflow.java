package com.skytrace.backend.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EvidenceDerivativeWorkflow {
    @WorkflowMethod
    void enrich(String evidenceCode);
}
