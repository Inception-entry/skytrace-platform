package com.skytrace.backend.task;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.task.dto.CreateInspectionTaskRequest;
import com.skytrace.backend.task.dto.InspectionTaskResponse;
import com.skytrace.backend.task.dto.UpdateInspectionTaskRequest;
import com.skytrace.backend.task.service.InspectionTaskService;
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

    public InspectionTaskController(InspectionTaskService inspectionTaskService) {
        this.inspectionTaskService = inspectionTaskService;
    }

    @GetMapping
    public ApiResponse<List<InspectionTaskResponse>> list() {
        return ApiResponse.ok(inspectionTaskService.findAll());
    }

    @GetMapping("/{taskCode}")
    public ApiResponse<InspectionTaskResponse> detail(@PathVariable String taskCode) {
        return ApiResponse.ok(inspectionTaskService.findByTaskCode(taskCode));
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
