package com.skytrace.backend.evidence.service;

import com.skytrace.backend.temporal.workflow.EvidenceDerivativeWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EvidenceDerivativeJobService {

    private static final Logger log =
            LoggerFactory.getLogger(EvidenceDerivativeJobService.class);

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public EvidenceDerivativeJobService(
            WorkflowClient workflowClient,
            @Value("${TEMPORAL_TASK_QUEUE:skytrace-inspection-task-queue}")
            String taskQueue) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    public void start(String evidenceCode) {
        try {
            EvidenceDerivativeWorkflow workflow = workflowClient.newWorkflowStub(
                    EvidenceDerivativeWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(taskQueue)
                            .setWorkflowId("evidence-derivative-" + evidenceCode)
                            .build()
            );
            WorkflowClient.start(workflow::enrich, evidenceCode);
        } catch (Exception exception) {
            log.warn(
                    "启动证据衍生工作流失败 evidenceCode={}: {}",
                    evidenceCode,
                    exception.getMessage()
            );
        }
    }
}
