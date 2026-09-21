package com.skytrace.backend.evidence.service;

import com.skytrace.backend.temporal.workflow.EvidenceArchiveWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceArchiveWorkflowStarter {

    private static final Logger log =
            LoggerFactory.getLogger(EvidenceArchiveWorkflowStarter.class);

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public EvidenceArchiveWorkflowStarter(
            WorkflowClient workflowClient,
            @Value("${TEMPORAL_TASK_QUEUE:skytrace-inspection-task-queue}")
            String taskQueue) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    public void start(String jobCode) {
        try {
            EvidenceArchiveWorkflow workflow = workflowClient.newWorkflowStub(
                    EvidenceArchiveWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(taskQueue)
                            .setWorkflowId("evidence-archive-" + jobCode)
                            .build()
            );
            WorkflowClient.start(workflow::archive, jobCode);
        } catch (RuntimeException exception) {
            if (EvidenceWorkflowStarts.alreadyStarted(exception)) {
                log.info("event=evidence_archive_already_started jobCode={}", jobCode);
                return;
            }
            log.warn(
                    "event=evidence_archive_start_failed jobCode={} reason={}",
                    jobCode,
                    exception.getMessage()
            );
            throw exception;
        }
    }
}
