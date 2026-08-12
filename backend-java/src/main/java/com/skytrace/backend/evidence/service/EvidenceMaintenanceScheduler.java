package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.dto.EvidenceCleanupBatchResponse;
import com.skytrace.backend.evidence.dto.EvidenceHashBackfillResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            EvidenceMaintenanceScheduler.class
    );

    private final EvidenceHashBackfillService hashBackfillService;
    private final EvidenceCleanupService cleanupService;
    private final EvidenceMaintenanceProperties properties;
    // 单进程内拒绝同类任务重叠；跨实例互斥由数据库原子认领保证。
    private final AtomicBoolean hashBackfillRunning = new AtomicBoolean(false);
    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    public EvidenceMaintenanceScheduler(
            EvidenceHashBackfillService hashBackfillService,
            EvidenceCleanupService cleanupService,
            EvidenceMaintenanceProperties properties) {
        this.hashBackfillService = hashBackfillService;
        this.cleanupService = cleanupService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${app.evidence.maintenance.hash-backfill-fixed-delay-ms:300000}",
            initialDelayString = "${app.evidence.maintenance.hash-backfill-initial-delay-ms:60000}"
    )
    public void runHashBackfill() {
        // 默认关闭，只有运维显式启用后才读取历史 MinIO 对象。
        if (!properties.isHashBackfillEnabled()
                || !hashBackfillRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            EvidenceHashBackfillResponse result =
                    hashBackfillService.runBatch(null);
            log.info(
                    "event=evidence_hash_backfill_completed selected={} claimed={} succeeded={} failed={}",
                    result.selected(),
                    result.claimed(),
                    result.succeeded(),
                    result.failed()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "event=evidence_hash_backfill_batch_failed exceptionType={}",
                    exception.getClass().getSimpleName()
            );
        } finally {
            hashBackfillRunning.set(false);
        }
    }

    @Scheduled(cron = "${app.evidence.maintenance.cleanup-cron:0 30 3 * * *}")
    public void runCleanup() {
        // cleanupEnabled 控制是否调度，cleanupDryRun 决定只演练还是实际删除。
        if (!properties.isCleanupEnabled()
                || !cleanupRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            EvidenceCleanupBatchResponse result = cleanupService.runBatch(
                    properties.isCleanupDryRun(),
                    null
            );
            log.info(
                    "event=evidence_cleanup_completed dryRun={} eligible={} selected={} purged={} failed={}",
                    result.dryRun(),
                    result.eligibleCount(),
                    result.selected(),
                    result.purged(),
                    result.failed()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "event=evidence_cleanup_batch_failed exceptionType={}",
                    exception.getClass().getSimpleName()
            );
        } finally {
            cleanupRunning.set(false);
        }
    }
}
