package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceArchiveScopeType;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import com.skytrace.backend.evidence.service.EvidenceArchivePackageService;
import com.skytrace.backend.evidence.service.EvidenceHashService;
import com.skytrace.backend.evidence.service.EvidenceManifestService;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Component("evidenceArchiveActivities")
public class EvidenceArchiveActivitiesImpl implements EvidenceArchiveActivities {

    private static final Logger log = LoggerFactory.getLogger(
            EvidenceArchiveActivitiesImpl.class
    );

    private final EvidenceArchiveJobRepository jobRepository;
    private final EvidenceAssetRepository assetRepository;
    private final ObjectProvider<EvidenceHashService> hashService;
    private final EvidenceManifestService manifestService;
    private final ObjectProvider<EvidenceArchivePackageService> archivePackageService;

    public EvidenceArchiveActivitiesImpl(
            EvidenceArchiveJobRepository jobRepository,
            EvidenceAssetRepository assetRepository,
            ObjectProvider<EvidenceHashService> hashService,
            EvidenceManifestService manifestService,
            ObjectProvider<EvidenceArchivePackageService> archivePackageService) {
        this.jobRepository = jobRepository;
        this.assetRepository = assetRepository;
        this.hashService = hashService;
        this.manifestService = manifestService;
        this.archivePackageService = archivePackageService;
    }

    @Override
    public void markRunning(String jobCode) {
        EvidenceArchiveJob job = requireJob(jobCode);
        job.setStatus(EvidenceArchiveJobStatus.RUNNING);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    @Override
    public void executeArchive(String jobCode) {
        EvidenceArchiveJob job = requireJob(jobCode);
        List<EvidenceAsset> assets = loadAssets(job);
        if (assets.isEmpty()) {
            throw new IllegalStateException("归档范围内没有可导出的证据");
        }

        EvidenceHashService hashing = requireHashService();
        for (int index = 0; index < assets.size(); index++) {
            EvidenceAsset asset = assets.get(index);
            // 哈希前先发送进度，单个大对象处理期间至少已有最新活动时间。
            heartbeat("HASHING", index, assets.size(), asset.getEvidenceCode());
            // 归档清单和 checksums 都依赖内容哈希，因此先补齐缺失值。
            hashing.ensureContentHash(asset);
            // 一个对象哈希完成后更新计数，Temporal UI 可观察实际推进。
            heartbeat(
                    "HASHED",
                    index + 1,
                    assets.size(),
                    asset.getEvidenceCode()
            );
        }

        List<EvidenceManifestService.ArchivedEvidenceFile> files =
                manifestService.describe(assets);
        byte[] manifestBytes = manifestService.buildManifest(job, files);
        byte[] checksumsBytes = manifestService.buildChecksums(files);
        EvidenceArchivePackageService packageService =
                requireArchivePackageService();
        // 打包服务负责把 manifest、checksum 和原始证据对象一起组装成 ZIP 并回写 MinIO。
        EvidenceArchivePackageService.ArchivePackageResult result =
                packageService.buildAndStore(
                        job,
                        files,
                        manifestBytes,
                        checksumsBytes,
                        (stage, completed, total, processedBytes) ->
                                heartbeat(
                                        stage,
                                        completed,
                                        total,
                                        Long.toString(processedBytes)
                                )
                );

        LocalDateTime archivedAt = LocalDateTime.now();
        for (EvidenceAsset asset : assets) {
            asset.setArchiveStatus(EvidenceArchiveStatus.ARCHIVED);
            asset.setArchiveBatchCode(job.getJobCode());
            asset.setArchivedAt(archivedAt);
        }
        // 参与归档的证据统一打上同一批次号，便于后续追踪和清理策略联动。
        assetRepository.saveAll(assets);

        job.setStatus(EvidenceArchiveJobStatus.COMPLETED);
        job.setOutputBucket(result.bucket());
        job.setOutputObjectKey(result.packageObjectKey());
        job.setManifestObjectKey(result.manifestObjectKey());
        // 保存 ZIP 自身哈希，物理清理前必须从 MinIO 重算并匹配它。
        job.setPackageContentHash(result.packageContentHash());
        // 新归档尚未执行清理前完整性复核，因此验证时间保持为空。
        job.setPackageVerifiedAt(null);
        job.setTotalFiles(result.totalFiles());
        job.setTotalBytes(result.totalBytes());
        job.setCompletedAt(archivedAt);
        job.setErrorMessage(null);
        jobRepository.save(job);
    }

    @Override
    public void markFailed(String jobCode, String errorMessage) {
        EvidenceArchiveJob job = requireJob(jobCode);
        job.setStatus(EvidenceArchiveJobStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(truncate(errorMessage));
        jobRepository.save(job);
    }

    private List<EvidenceAsset> loadAssets(EvidenceArchiveJob job) {
        return switch (job.getScopeType()) {
            // 第一版只支持按任务或告警做确定性归档，避免开放复杂条件导出。
            case TASK -> assetRepository.findByTaskCodeAndDeletedFalseOrderByCreatedAtAsc(
                    job.getScopeValue()
            );
            case ALARM -> assetRepository
                    .findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtAsc(
                            job.getScopeValue()
                    );
            case CASE -> throw new IllegalArgumentException(
                    "当前版本暂不支持 CASE 归档范围"
            );
        };
    }

    private EvidenceArchiveJob requireJob(String jobCode) {
        return jobRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new NoSuchElementException(
                        "归档任务不存在: " + jobCode
                ));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private EvidenceHashService requireHashService() {
        EvidenceHashService service = hashService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("MinIO is disabled; archive hashing is unavailable");
        }
        return service;
    }

    private EvidenceArchivePackageService requireArchivePackageService() {
        EvidenceArchivePackageService service =
                archivePackageService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("MinIO is disabled; archive packaging is unavailable");
        }
        return service;
    }

    private void heartbeat(
            String stage,
            int completed,
            int total,
            String detail) {
        try {
            // 只有 Temporal Worker 线程拥有 ActivityExecutionContext。
            ActivityExecutionContext context = Activity.getExecutionContext();
            // record 会由 Temporal DataConverter 序列化到 Heartbeat details。
            context.heartbeat(new ArchiveHeartbeat(
                    stage,
                    completed,
                    total,
                    detail
            ));
        } catch (IllegalStateException outsideTemporalWorker) {
            // 单元测试会直接调用 Activity 实现，此时没有 Temporal 上下文，安全跳过。
            log.debug(
                    "event=evidence_archive_heartbeat_skipped stage={} reason=outside_worker",
                    stage
            );
        }
    }

    public record ArchiveHeartbeat(
            String stage,
            int completed,
            int total,
            String detail
    ) {
    }
}
