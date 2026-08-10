package com.skytrace.backend.evidence;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.dto.EvidenceDetailResponse;
import com.skytrace.backend.evidence.dto.EvidencePageResponse;
import com.skytrace.backend.evidence.dto.EvidenceSearchRequest;
import com.skytrace.backend.evidence.dto.EvidenceUploadResponse;
import com.skytrace.backend.evidence.service.EvidenceAccessService;
import com.skytrace.backend.evidence.service.EvidenceCommandService;
import com.skytrace.backend.evidence.service.EvidenceQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/evidence")
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceController {

    private final EvidenceQueryService queryService;
    private final EvidenceCommandService commandService;
    private final EvidenceAccessService accessService;

    public EvidenceController(
            EvidenceQueryService queryService,
            EvidenceCommandService commandService,
            EvidenceAccessService accessService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.accessService = accessService;
    }

    @GetMapping
    public ApiResponse<List<EvidenceAssetResponse>> list(
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode) {
        return ApiResponse.ok(queryService.findLegacy(taskCode, alarmEventCode));
    }

    @GetMapping("/search")
    public ApiResponse<EvidencePageResponse> search(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode,
            @RequestParam(value = "deviceCode", required = false)
            String deviceCode,
            @RequestParam(value = "assetType", required = false)
            String assetType,
            @RequestParam(value = "sourceType", required = false)
            String sourceType,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant endTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", required = false)
            Boolean includeDeleted) {
        return ApiResponse.ok(queryService.search(new EvidenceSearchRequest(
                page,
                size,
                taskCode,
                alarmEventCode,
                deviceCode,
                assetType,
                sourceType,
                startTime,
                endTime,
                keyword,
                includeDeleted
        )));
    }

    @GetMapping("/{evidenceCode}")
    public ApiResponse<EvidenceDetailResponse> detail(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(queryService.detail(evidenceCode));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode,
            @RequestParam(value = "deviceCode", required = false)
            String deviceCode) {
        return ApiResponse.ok(commandService.upload(
                file,
                taskCode,
                alarmEventCode,
                deviceCode
        ));
    }

    @PostMapping("/{evidenceCode}/preview-url")
    public ApiResponse<EvidenceAccessUrlResponse> previewUrl(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(accessService.createPreviewUrl(evidenceCode));
    }

    @PostMapping("/{evidenceCode}/download-url")
    public ApiResponse<EvidenceAccessUrlResponse> downloadUrl(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(accessService.createDownloadUrl(evidenceCode));
    }

    @DeleteMapping("/{evidenceCode}")
    public ApiResponse<Void> delete(@PathVariable String evidenceCode) {
        commandService.softDelete(evidenceCode);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{evidenceCode}/restore")
    public ApiResponse<Void> restore(@PathVariable String evidenceCode) {
        commandService.restore(evidenceCode);
        return ApiResponse.ok(null);
    }
}