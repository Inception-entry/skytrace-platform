package com.uav.backend.evidence;

import com.uav.backend.common.ApiResponse;
import com.uav.backend.evidence.dto.EvidenceAssetResponse;
import com.uav.backend.evidence.dto.EvidenceUploadResponse;
import com.uav.backend.evidence.service.EvidenceStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/evidence")
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceController {

    private final EvidenceStorageService storageService;

    public EvidenceController(EvidenceStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    public ApiResponse<List<EvidenceAssetResponse>> list(
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode) {
        return ApiResponse.ok(
                storageService.findEvidence(taskCode, alarmEventCode)
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode) {
        return ApiResponse.ok(
                storageService.upload(file, taskCode, alarmEventCode)
        );
    }
}
