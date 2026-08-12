package com.skytrace.backend.task;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.task.dto.CreateInspectionTaskRequest;
import com.skytrace.backend.task.dto.InspectionTaskResponse;
import com.skytrace.backend.task.dto.UpdateInspectionTaskRequest;
import com.skytrace.backend.task.service.InspectionTaskService;
import com.skytrace.backend.telemetry.dto.DeviceTelemetryPointResponse;
import com.skytrace.backend.telemetry.service.DeviceTelemetryHistoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/inspection-tasks")
public class InspectionTaskController {
    private final InspectionTaskService inspectionTaskService;
    private final DeviceTelemetryHistoryService telemetryHistoryService;

    public InspectionTaskController(
            InspectionTaskService inspectionTaskService,
            DeviceTelemetryHistoryService telemetryHistoryService) {
        this.inspectionTaskService = inspectionTaskService;
        this.telemetryHistoryService = telemetryHistoryService;
    }

    @GetMapping
    public ApiResponse<List<InspectionTaskResponse>> list() {
        return ApiResponse.ok(inspectionTaskService.findAll());
    }

    @GetMapping("/{taskCode}")
    public ApiResponse<InspectionTaskResponse> detail(@PathVariable String taskCode) {
        return ApiResponse.ok(inspectionTaskService.findByTaskCode(taskCode));
    }

    @GetMapping("/{taskCode}/telemetry")
    public ApiResponse<List<DeviceTelemetryPointResponse>> telemetry(
            @PathVariable String taskCode) {
        // 先确认任务真实存在，不存在的任务返回 404 而非空轨迹。
        inspectionTaskService.findByTaskCode(taskCode);
        return ApiResponse.ok(
                telemetryHistoryService.findTrackByTaskCode(taskCode)
        );
    }

    @PostMapping
    public ApiResponse<InspectionTaskResponse> create(
            @Valid @RequestBody CreateInspectionTaskRequest request) {
        return ApiResponse.ok(inspectionTaskService.create(request));
    }

    @PutMapping("/{taskCode}")
    public ApiResponse<InspectionTaskResponse> update(
            @PathVariable String taskCode,
            @Valid @RequestBody UpdateInspectionTaskRequest request) {
        return ApiResponse.ok(
                inspectionTaskService.update(taskCode, request)
        );
    }
}
