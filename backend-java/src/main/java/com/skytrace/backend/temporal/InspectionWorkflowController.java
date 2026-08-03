package com.skytrace.backend.temporal;

import com.skytrace.backend.ai.dto.InspectionAnalysisRequest;
import com.skytrace.backend.ai.dto.InspectionAnalysisResponse;
import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.task.service.InspectionTaskService;
import com.skytrace.backend.temporal.workflow.InspectionChatWorkflow;
import com.skytrace.backend.temporal.workflow.InspectionWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/inspection-workflows")
public class InspectionWorkflowController {

    private static final String TASK_QUEUE = "skytrace-inspection-task-queue";

    private final WorkflowClient workflowClient;
    private final InspectionTaskService inspectionTaskService;

    public InspectionWorkflowController(
            WorkflowClient workflowClient,
            InspectionTaskService inspectionTaskService) {
        this.workflowClient = workflowClient;
        this.inspectionTaskService = inspectionTaskService;
    }

    @PostMapping("/{taskCode}")
    public ApiResponse<Map<String, String>> start(
            @PathVariable String taskCode) {
        InspectionWorkflow workflow = workflowClient.newWorkflowStub(
                InspectionWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("inspection-" + taskCode)
                        .setTaskQueue(TASK_QUEUE)
                        .build()
        );

        WorkflowExecution execution =
                WorkflowClient.start(workflow::start, taskCode);

        return ApiResponse.ok(Map.of(
                "workflowId", execution.getWorkflowId(),
                "runId", execution.getRunId()
        ));
    }

    @GetMapping("/{taskCode}/status")
    public ApiResponse<Map<String, String>> status(
            @PathVariable String taskCode) {
        InspectionWorkflow workflow = getWorkflow(taskCode);

        return ApiResponse.ok(Map.of(
                "taskCode", taskCode,
                "status", workflow.getStatus(),
                "lastAlarmEventCode", workflow.getLastAlarmEventCode()
        ));
    }

    @PostMapping("/{taskCode}/complete")
    public ApiResponse<Void> complete(@PathVariable String taskCode) {
        getWorkflow(taskCode).complete();
        return ApiResponse.ok(null);
    }

    @PostMapping("/{taskCode}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String taskCode) {
        getWorkflow(taskCode).cancel();
        return ApiResponse.ok(null);
    }

    @PostMapping("/{taskCode}/analysis")
    public ApiResponse<InspectionAnalysisResponse> analyze(
            @PathVariable String taskCode,
            @Valid @RequestBody InspectionAnalysisRequest request) {
        // 在创建工作流前确认任务真实存在，让不存在的任务直接返回 404。
        inspectionTaskService.findByTaskCode(taskCode);

        String workflowId = "inspection-analysis-"
                + taskCode + "-" + UUID.randomUUID();

        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = workflowId;
        }

        InspectionChatWorkflow workflow =
                workflowClient.newWorkflowStub(
                        InspectionChatWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue(TASK_QUEUE)
                                .build()
                );

        String analysis = workflow.chat(
                taskCode,
                sessionId,
                request.question()
        );

        return ApiResponse.ok(new InspectionAnalysisResponse(
                taskCode,
                workflowId,
                analysis
        ));
    }

    private InspectionWorkflow getWorkflow(String taskCode) {
        return workflowClient.newWorkflowStub(
                InspectionWorkflow.class,
                "inspection-" + taskCode
        );
    }
}
