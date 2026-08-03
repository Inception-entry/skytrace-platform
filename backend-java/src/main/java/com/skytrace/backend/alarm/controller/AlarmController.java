package com.skytrace.backend.alarm.controller;

import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.CreateAlarmRequest;
import com.skytrace.backend.alarm.service.AlarmService;
import com.skytrace.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alarms")
public class AlarmController {
    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping
    public ApiResponse<AlarmResponse> create(@Valid @RequestBody CreateAlarmRequest request) {
        return ApiResponse.ok(alarmService.create(request));
    }

    @GetMapping("/latest")
    public ApiResponse<List<AlarmResponse>> latest() {
        return ApiResponse.ok(alarmService.latest());
    }
}
