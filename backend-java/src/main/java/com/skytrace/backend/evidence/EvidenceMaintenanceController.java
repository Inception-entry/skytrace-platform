package com.skytrace.backend.evidence;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.evidence.dto.EvidenceCleanupBatchResponse;
import com.skytrace.backend.evidence.dto.EvidenceHashBackfillResponse;
import com.skytrace.backend.evidence.dto.EvidenceMaintenancePolicyResponse;
import com.skytrace.backend.evidence.service.EvidenceCleanupService;
import com.skytrace.backend.evidence.service.EvidenceHashBackfillService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/evidence-maintenance")
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceMaintenanceController {

    // 正式删除必须由管理员原样提供确认串，避免误点或脚本参数遗漏。
    private static final String PURGE_CONFIRMATION =
            "PURGE_ARCHIVED_EVIDENCE";

    private final EvidenceHashBackfillService hashBackfillService;
    private final EvidenceCleanupService cleanupService;
    private final EvidenceMaintenanceProperties properties;

    public EvidenceMaintenanceController(
            EvidenceHashBackfillService hashBackfillService,
            EvidenceCleanupService cleanupService,
            EvidenceMaintenanceProperties properties) {
        this.hashBackfillService = hashBackfillService;
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    @GetMapping("/policy")
    public ApiResponse<EvidenceMaintenancePolicyResponse> policy() {
        // 返回当前生效策略，管理员无需登录服务器读取环境变量。
        return ApiResponse.ok(new EvidenceMaintenancePolicyResponse(
                properties.isHashBackfillEnabled(),
                properties.getHashBackfillBatchSize(),
                properties.getHashBackfillRetryHours(),
                properties.isCleanupEnabled(),
                properties.isCleanupDryRun(),
                properties.getCleanupRetentionDays(),
                properties.getCleanupBatchSize(),
                properties.getCleanupCron(),
                "deleted=true，ARCHIVED，contentHash 已存在，归档和软删除均超过保留期，"
                        + "COMPLETED 归档包通过 SHA-256 校验，且 manifest 与当前证据逐项一致"
        ));
    }

    @PostMapping("/hash-backfill")
    public ApiResponse<EvidenceHashBackfillResponse> hashBackfill(
            @RequestParam(required = false) Integer batchSize) {
        // 手动触发不依赖 scheduler enabled，便于先用小批次灰度验证。
        return ApiResponse.ok(hashBackfillService.runBatch(batchSize));
    }

    @GetMapping("/cleanup-preview")
    public ApiResponse<EvidenceCleanupBatchResponse> cleanupPreview(
            @RequestParam(required = false) Integer batchSize) {
        // GET 预览是纯查询，不认领记录也不访问 MinIO 删除接口。
        return ApiResponse.ok(cleanupService.preview(batchSize));
    }

    @PostMapping("/cleanup")
    public ApiResponse<EvidenceCleanupBatchResponse> cleanup(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) String confirmation) {
        // dryRun=false 时必须显式提供固定确认串，默认请求永远只是演练。
        if (!dryRun && !PURGE_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException(
                    "正式清理必须提供 confirmation=" + PURGE_CONFIRMATION
            );
        }
        return ApiResponse.ok(cleanupService.runBatch(
                dryRun,
                batchSize
        ));
    }
}
