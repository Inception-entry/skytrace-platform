package com.uav.backend.evidence;

import com.uav.backend.common.ApiResponse;
import com.uav.backend.evidence.dto.EvidenceUploadResponse;
import com.uav.backend.evidence.service.EvidenceStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/evidence")
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceController {

    private final EvidenceStorageService storageService;

    public EvidenceController(EvidenceStorageService storageService) {
        this.storageService = storageService;
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
