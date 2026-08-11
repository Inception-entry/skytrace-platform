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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Component("evidenceArchiveActivities")
public class EvidenceArchiveActivitiesImpl implements EvidenceArchiveActivities {

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
        for (EvidenceAsset asset : assets) {
            hashing.ensureContentHash(asset);
        }

        List<EvidenceManifestService.ArchivedEvidenceFile> files =
                manifestService.describe(assets);
        byte[] manifestBytes = manifestService.buildManifest(job, files);
        byte[] checksumsBytes = manifestService.buildChecksums(files);
        EvidenceArchivePackageService packageService =
                requireArchivePackageService();
        EvidenceArchivePackageService.ArchivePackageResult result =
                packageService.buildAndStore(
                        job,
                        files,
                        manifestBytes,
                        checksumsBytes
                );

        LocalDateTime archivedAt = LocalDateTime.now();
        for (EvidenceAsset asset : assets) {
            asset.setArchiveStatus(EvidenceArchiveStatus.ARCHIVED);
            asset.setArchiveBatchCode(job.getJobCode());
            asset.setArchivedAt(archivedAt);
        }
        assetRepository.saveAll(assets);

        job.setStatus(EvidenceArchiveJobStatus.COMPLETED);
        job.setOutputBucket(result.bucket());
        job.setOutputObjectKey(result.packageObjectKey());
        job.setManifestObjectKey(result.manifestObjectKey());
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
}
