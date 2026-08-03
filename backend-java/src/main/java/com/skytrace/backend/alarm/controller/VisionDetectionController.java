package com.skytrace.backend.alarm.controller;

import com.skytrace.backend.ai.client.AiVisionClient;
import com.skytrace.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/detections")
public class VisionDetectionController {

    private final AiVisionClient aiVisionClient;

    public VisionDetectionController(AiVisionClient aiVisionClient) {
        this.aiVisionClient = aiVisionClient;
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deviceCode", defaultValue = "UAV-001")
            String deviceCode,
            @RequestParam(value = "taskCode", required = false)
            String taskCode,
            @RequestParam(value = "latitude", required = false)
            Double latitude,
            @RequestParam(value = "longitude", required = false)
            Double longitude,
            @RequestParam(value = "publishAlarms", defaultValue = "true")
            boolean publishAlarms,
            @RequestParam(value = "maxAlarms", required = false)
            Integer maxAlarms) {
        return ApiResponse.ok(aiVisionClient.analyze(
                file,
                deviceCode,
                taskCode,
                latitude,
                longitude,
                publishAlarms,
                maxAlarms
        ));
    }

    @PostMapping("/analyze-video")
    public ApiResponse<Map<String, Object>> analyzeVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deviceCode", defaultValue = "UAV-001")
            String deviceCode,
            @RequestParam(value = "taskCode", required = false)
            String taskCode,
            @RequestParam(value = "latitude", required = false)
            Double latitude,
            @RequestParam(value = "longitude", required = false)
            Double longitude,
            @RequestParam(value = "publishAlarms", defaultValue = "true")
            boolean publishAlarms,
            @RequestParam(value = "maxAlarms", required = false)
            Integer maxAlarms,
            @RequestParam(value = "frameIntervalSec", required = false)
            Double frameIntervalSec,
            @RequestParam(value = "maxFrames", required = false)
            Integer maxFrames) {
        return ApiResponse.ok(aiVisionClient.analyzeVideo(
                file,
                deviceCode,
                taskCode,
                latitude,
                longitude,
                publishAlarms,
                maxAlarms,
                frameIntervalSec,
                maxFrames
        ));
    }
}
