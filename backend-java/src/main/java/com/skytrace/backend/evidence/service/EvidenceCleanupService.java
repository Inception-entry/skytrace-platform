package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceCleanupBatchResponse;
import com.skytrace.backend.evidence.dto.EvidenceCleanupItemResponse;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceCleanupService {

    private static final Logger log = LoggerFactory.getLogger(
            EvidenceCleanupService.class
    );
    // 手动接口也不能一次认领无限记录，避免误操作扩大影响面。
    private static final int MAX_BATCH_SIZE = 200;

    private final EvidenceAssetRepository assetRepository;
    private final EvidenceArchiveJobRepository jobRepository;
    private final EvidenceArchiveIntegrityService integrityService;
    private final EvidenceStorageService storageService;
    private final EvidenceMaintenanceAuditService auditService;
    private final EvidenceMaintenanceProperties properties;

    public EvidenceCleanupService(
            EvidenceAssetRepository assetRepository,
            EvidenceArchiveJobRepository jobRepository,
            EvidenceArchiveIntegrityService integrityService,
            EvidenceStorageService storageService,
            EvidenceMaintenanceAuditService auditService,
            EvidenceMaintenanceProperties properties) {
        this.assetRepository = assetRepository;
        this.jobRepository = jobRepository;
        this.integrityService = integrityService;
        this.storageService = storageService;
        this.auditService = auditService;
        this.properties = properties;
    }

    public EvidenceCleanupBatchResponse preview(Integer requestedBatchSize) {
        // 预览和真实执行使用完全相同的保留期与候选查询，避免“看见一批、删另一批”。
        return execute(true, requestedBatchSize);
    }

    public EvidenceCleanupBatchResponse runBatch(
            boolean dryRun,
            Integer requestedBatchSize) {
        // Scheduler 和管理员接口都通过同一个入口，安全规则不会分叉。
        return execute(dryRun, requestedBatchSize);
    }

    private EvidenceCleanupBatchResponse execute(
            boolean dryRun,
            Integer requestedBatchSize) {
        // 保留期至少一天，错误配置不能让刚归档对象立刻成为候选。
        int retentionDays = Math.max(
                properties.getCleanupRetentionDays(),
                1
        );
        // 数据库 DATETIME 没有时区，必须沿用应用时区才能与既有 archivedAt/deletedAt 对齐。
        ZonedDateTime cutoffDateTime = ZonedDateTime.now()
                .minusDays(retentionDays);
        // API 仍返回带时区语义的 Instant，便于运维准确理解本轮边界。
        Instant cutoffInstant = cutoffDateTime.toInstant();
        LocalDateTime cutoff = cutoffDateTime.toLocalDateTime();
        int batchSize = normalizeBatchSize(
                requestedBatchSize,
                properties.getCleanupBatchSize()
        );

        // eligibleCount 展示完整待处理规模，selected 只表示本轮安全批次。
        long eligibleCount = assetRepository.countPurgeCandidates(
                EvidenceArchiveStatus.ARCHIVED,
                cutoff
        );
        List<EvidenceAsset> candidates = assetRepository.findPurgeCandidates(
                EvidenceArchiveStatus.ARCHIVED,
                cutoff,
                PageRequest.of(0, batchSize)
        );

        if (dryRun) {
            // 演练绝不修改数据库状态，也不会调用 MinIO 删除 API。
            List<EvidenceCleanupItemResponse> items = candidates.stream()
                    .map(asset -> new EvidenceCleanupItemResponse(
                            asset.getEvidenceCode(),
                            asset.getArchiveBatchCode(),
                            "ELIGIBLE",
                            "满足软删除、归档完成和保留期候选条件"
                    ))
                    .toList();
            return response(
                    true,
                    retentionDays,
                    cutoffInstant,
                    eligibleCount,
                    candidates.size(),
                    0,
                    0,
                    0,
                    0,
                    items
            );
        }

        // 正式执行前先释放进程崩溃遗留的超时认领。
        int staleClaimsReleased = assetRepository.releaseStalePurgeClaims(
                EvidenceArchiveStatus.PURGING,
                EvidenceArchiveStatus.ARCHIVED,
                LocalDateTime.now().minusHours(
                        Math.max(properties.getCleanupStaleClaimHours(), 1)
                ),
                "上一次清理认领超时，已自动释放"
        );

        int claimed = 0;
        int purged = 0;
        int failed = 0;
        List<EvidenceCleanupItemResponse> items = new ArrayList<>();
        // 同一批次的多条证据只验证一次大 ZIP，避免重复下载归档包。
        Map<String, VerificationResult> verificationCache =
                new LinkedHashMap<>();

        for (EvidenceAsset candidate : candidates) {
            LocalDateTime claimedAt = LocalDateTime.now();
            // 原子状态转换是多实例清理任务之间的互斥边界。
            int updated = assetRepository.claimPurge(
                    candidate.getId(),
                    EvidenceArchiveStatus.ARCHIVED,
                    EvidenceArchiveStatus.PURGING,
                    cutoff,
                    claimedAt
            );
            if (updated == 0) {
                continue;
            }
            claimed++;
            candidate.setArchiveStatus(EvidenceArchiveStatus.PURGING);
            candidate.setPurgeStartedAt(claimedAt);
            candidate.setPurgeError(null);

            try {
                // 每个 archiveBatchCode 在本轮中只查询和校验一次归档任务。
                VerificationResult verification = verificationCache
                        .computeIfAbsent(
                                candidate.getArchiveBatchCode(),
                                this::verifyArchiveBatch
                        );
                if (!verification.valid()) {
                    throw new IllegalStateException(verification.message());
                }
                // 包级校验通过后还要核对当前证据确实在受保护的 manifest 中。
                integrityService.verifyContains(
                        verification.manifest(),
                        candidate
                );

                // 删除前先独立提交 STARTED 审计；审计写不进去时禁止继续删除。
                auditService.recordPurge(
                        candidate.getEvidenceCode(),
                        "STARTED",
                        102,
                        null
                );
                // LinkedHashSet 去重，避免原件与派生字段意外指向同一对象时重复调用。
                for (String objectKey : objectKeys(candidate)) {
                    storageService.removeEvidenceObject(
                            candidate.getBucket(),
                            objectKey
                    );
                }

                // 数据库保留证据编号、哈希、归档批次和原对象键，仅标记内容已清理。
                candidate.setArchiveStatus(EvidenceArchiveStatus.PURGED);
                candidate.setPurgedAt(LocalDateTime.now());
                candidate.setPurgeStartedAt(null);
                candidate.setPurgeError(null);
                assetRepository.save(candidate);
                purged++;
                items.add(new EvidenceCleanupItemResponse(
                        candidate.getEvidenceCode(),
                        candidate.getArchiveBatchCode(),
                        "PURGED",
                        "MinIO 原件和派生对象已物理删除，数据库墓碑已保留"
                ));
                // STARTED 已经保证最低审计线，SUCCESS 记录失败不应把已删除内容伪装成可重试。
                recordCompletionAudit(candidate);
            } catch (RuntimeException exception) {
                failed++;
                String message = truncate(rootMessage(exception));
                // 任一步失败都恢复 ARCHIVED，后续经过人工修复可再次进入候选。
                candidate.setArchiveStatus(EvidenceArchiveStatus.ARCHIVED);
                candidate.setPurgeStartedAt(null);
                candidate.setPurgeError(message);
                assetRepository.save(candidate);
                items.add(new EvidenceCleanupItemResponse(
                        candidate.getEvidenceCode(),
                        candidate.getArchiveBatchCode(),
                        "FAILED",
                        message
                ));
                recordFailureAudit(candidate, exception);
                log.warn(
                        "event=evidence_cleanup_failed evidenceCode={} archiveBatchCode={} exceptionType={}",
                        candidate.getEvidenceCode(),
                        candidate.getArchiveBatchCode(),
                        exception.getClass().getSimpleName()
                );
            }
        }

        return response(
                false,
                retentionDays,
                cutoffInstant,
                eligibleCount,
                candidates.size(),
                claimed,
                purged,
                failed,
                staleClaimsReleased,
                items
        );
    }

    private VerificationResult verifyArchiveBatch(String jobCode) {
        try {
            // 候选必须能追溯到同名归档任务，不能只相信资产表上的字符串。
            EvidenceArchiveJob job = jobRepository.findByJobCode(jobCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "找不到归档任务: " + jobCode
                    ));
            // 完整性服务负责状态、对象存在性和 SHA-256 三层校验。
            EvidenceArchiveIntegrityService.VerifiedArchiveManifest manifest =
                    integrityService.verifyBeforePurge(job);
            return new VerificationResult(
                    true,
                    "归档包校验通过",
                    manifest
            );
        } catch (RuntimeException exception) {
            return new VerificationResult(
                    false,
                    rootMessage(exception),
                    null
            );
        }
    }

    private Set<String> objectKeys(EvidenceAsset asset) {
        // 原件、图片缩略图和视频封面都属于可再生/可清理的在线对象。
        Set<String> keys = new LinkedHashSet<>();
        addIfPresent(keys, asset.getObjectKey());
        addIfPresent(keys, asset.getThumbnailObjectKey());
        addIfPresent(keys, asset.getPosterObjectKey());
        return keys;
    }

    private void recordCompletionAudit(EvidenceAsset candidate) {
        try {
            // 第二条审计明确表示数据库墓碑也已成功写入。
            auditService.recordPurge(
                    candidate.getEvidenceCode(),
                    "SUCCESS",
                    200,
                    null
            );
        } catch (RuntimeException auditException) {
            // 内容已经删除且 STARTED 审计已提交，此处只能告警，不能恢复成 ARCHIVED。
            log.error(
                    "event=evidence_cleanup_success_audit_failed evidenceCode={} exceptionType={}",
                    candidate.getEvidenceCode(),
                    auditException.getClass().getSimpleName()
            );
        }
    }

    private void recordFailureAudit(
            EvidenceAsset candidate,
            RuntimeException exception) {
        try {
            // 校验或删除失败同样写审计，便于管理员按 FAILURE 检索。
            auditService.recordPurge(
                    candidate.getEvidenceCode(),
                    "FAILURE",
                    500,
                    exception
            );
        } catch (RuntimeException auditException) {
            // 失败审计不能再次覆盖真正的清理错误，只记录结构化日志。
            log.error(
                    "event=evidence_cleanup_failure_audit_failed evidenceCode={} exceptionType={}",
                    candidate.getEvidenceCode(),
                    auditException.getClass().getSimpleName()
            );
        }
    }

    private static void addIfPresent(Set<String> keys, String objectKey) {
        // 空派生对象不参与删除，避免把空 key 交给 MinIO SDK。
        if (objectKey != null && !objectKey.isBlank()) {
            keys.add(objectKey);
        }
    }

    private static int normalizeBatchSize(
            Integer requested,
            int configured) {
        int value = requested == null ? configured : requested;
        return Math.min(Math.max(value, 1), MAX_BATCH_SIZE);
    }

    private static EvidenceCleanupBatchResponse response(
            boolean dryRun,
            int retentionDays,
            Instant cutoff,
            long eligibleCount,
            int selected,
            int claimed,
            int purged,
            int failed,
            int staleClaimsReleased,
            List<EvidenceCleanupItemResponse> items) {
        return new EvidenceCleanupBatchResponse(
                dryRun,
                retentionDays,
                cutoff,
                eligibleCount,
                selected,
                claimed,
                purged,
                failed,
                staleClaimsReleased,
                List.copyOf(items)
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static String truncate(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private record VerificationResult(
            boolean valid,
            String message,
            EvidenceArchiveIntegrityService.VerifiedArchiveManifest manifest) {
    }
}
