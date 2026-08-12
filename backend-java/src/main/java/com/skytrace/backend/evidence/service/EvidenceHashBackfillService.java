package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceHashBackfillResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceHashBackfillService {

    private static final Logger log = LoggerFactory.getLogger(
            EvidenceHashBackfillService.class
    );
    // 管理接口可以缩小批次，但不能绕过这个硬上限制造突发 MinIO 流量。
    private static final int MAX_BATCH_SIZE = 500;

    private final EvidenceAssetRepository repository;
    private final EvidenceHashService hashService;
    private final EvidenceMaintenanceProperties properties;

    public EvidenceHashBackfillService(
            EvidenceAssetRepository repository,
            EvidenceHashService hashService,
            EvidenceMaintenanceProperties properties) {
        this.repository = repository;
        this.hashService = hashService;
        this.properties = properties;
    }

    public EvidenceHashBackfillResponse runBatch(Integer requestedBatchSize) {
        // 对外请求为空时使用运维配置，并统一限制到安全范围。
        int batchSize = normalizeBatchSize(
                requestedBatchSize,
                properties.getHashBackfillBatchSize()
        );
        // 失败对象只有超过退避时间才可再次认领。
        // 项目现有 DATETIME 字段统一写入应用本地时间，认领时间沿用同一约定。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retryBefore = now.minusHours(
                Math.max(properties.getHashBackfillRetryHours(), 1)
        );
        // 查询顺序优先从未尝试的历史数据，永久坏对象不会堵住整个队列。
        List<EvidenceAsset> candidates = repository.findHashBackfillCandidates(
                EvidenceArchiveStatus.PURGED,
                retryBefore,
                PageRequest.of(0, batchSize)
        );

        int claimed = 0;
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (EvidenceAsset candidate : candidates) {
            // 原子 UPDATE 确保多个应用实例不会同时下载和计算同一个对象。
            int updated = repository.claimHashBackfill(
                    candidate.getId(),
                    EvidenceArchiveStatus.PURGED,
                    retryBefore,
                    now
            );
            if (updated == 0) {
                continue;
            }
            claimed++;
            // 同步内存实体，后续 save 不能把刚才的认领时间覆盖回 null。
            candidate.setHashBackfillAttemptedAt(now);
            candidate.setHashBackfillError(null);

            try {
                // 哈希服务使用输入流计算 SHA-256，不把历史对象读成完整 byte[]。
                hashService.ensureContentHash(candidate);
                // 成功记录清空旧错误，便于管理员区分已恢复和仍失败的对象。
                candidate.setHashBackfillError(null);
                repository.save(candidate);
                succeeded++;
            } catch (RuntimeException exception) {
                // 单对象失败不终止整批，后面的历史证据仍会继续回填。
                candidate.setHashBackfillError(truncate(rootMessage(exception)));
                repository.save(candidate);
                failures.add(candidate.getEvidenceCode());
                log.warn(
                        "event=evidence_hash_backfill_failed evidenceCode={} exceptionType={}",
                        candidate.getEvidenceCode(),
                        exception.getClass().getSimpleName()
                );
            }
        }

        // 返回稳定统计数据，管理员接口和定时任务日志可以共用。
        return new EvidenceHashBackfillResponse(
                candidates.size(),
                claimed,
                succeeded,
                failures.size(),
                List.copyOf(failures)
        );
    }

    private static int normalizeBatchSize(
            Integer requested,
            int configured) {
        // 请求值优先，但任何来源都必须至少为 1、最多为 500。
        int value = requested == null ? configured : requested;
        return Math.min(Math.max(value, 1), MAX_BATCH_SIZE);
    }

    private static String rootMessage(Throwable throwable) {
        // 顺着 cause 找到最底层原因，避免只保存“回填失败”这类包装信息。
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        // 无消息时至少记录异常类型，运维仍能定位错误类别。
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static String truncate(String value) {
        // 数据库字段长度为 512，提前截断比依赖数据库报错更可控。
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
