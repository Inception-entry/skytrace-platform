package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceCleanupBatchResponse;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceCleanupServiceTest {

    private final EvidenceAssetRepository assetRepository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceArchiveJobRepository jobRepository =
            mock(EvidenceArchiveJobRepository.class);
    private final EvidenceArchiveIntegrityService integrityService =
            mock(EvidenceArchiveIntegrityService.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceMaintenanceAuditService auditService =
            mock(EvidenceMaintenanceAuditService.class);
    private final EvidenceMaintenanceProperties properties =
            new EvidenceMaintenanceProperties();
    private EvidenceCleanupService service;

    @BeforeEach
    void setUp() {
        // 使用明确保留期和批次大小构造清理服务。
        properties.setCleanupRetentionDays(90);
        properties.setCleanupBatchSize(20);
        properties.setCleanupStaleClaimHours(6);
        service = new EvidenceCleanupService(
                assetRepository,
                jobRepository,
                integrityService,
                storageService,
                auditService,
                properties
        );
    }

    @Test
    void shouldPurgeOriginalAndDerivativesAfterArchiveVerification() {
        EvidenceAsset candidate = purgeCandidate();
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-001");
        stubCandidate(candidate);
        when(assetRepository.releaseStalePurgeClaims(
                eq(EvidenceArchiveStatus.PURGING),
                eq(EvidenceArchiveStatus.ARCHIVED),
                any(LocalDateTime.class),
                any(String.class)
        )).thenReturn(0);
        when(assetRepository.claimPurge(
                eq(1L),
                eq(EvidenceArchiveStatus.ARCHIVED),
                eq(EvidenceArchiveStatus.PURGING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(jobRepository.findByJobCode("AR-001"))
                .thenReturn(Optional.of(job));

        // 正式执行一条已经通过所有候选条件的证据。
        EvidenceCleanupBatchResponse result = service.runBatch(false, null);

        // 原件、缩略图和封面都必须通过受保护的存储删除边界处理。
        verify(storageService).removeEvidenceObject(
                "evidence",
                "TASK-001/source.jpg"
        );
        verify(storageService).removeEvidenceObject(
                "evidence",
                "derivatives/EV-001-thumb.jpg"
        );
        verify(storageService).removeEvidenceObject(
                "evidence",
                "derivatives/EV-001-poster.jpg"
        );
        verify(auditService).recordPurge("EV-001", "STARTED", 102, null);
        verify(auditService).recordPurge("EV-001", "SUCCESS", 200, null);
        // 数据库记录保留为墓碑，并清空短暂的认领状态。
        assertThat(candidate.getArchiveStatus())
                .isEqualTo(EvidenceArchiveStatus.PURGED);
        assertThat(candidate.getPurgedAt()).isNotNull();
        assertThat(candidate.getPurgeStartedAt()).isNull();
        assertThat(result.purged()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void dryRunShouldNeverClaimOrDeleteCandidate() {
        EvidenceAsset candidate = purgeCandidate();
        stubCandidate(candidate);

        // 预览使用真实候选查询，但到此为止没有任何副作用。
        EvidenceCleanupBatchResponse result = service.preview(10);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.items().getFirst().outcome()).isEqualTo("ELIGIBLE");
        verify(assetRepository, never()).claimPurge(
                any(Long.class),
                any(EvidenceArchiveStatus.class),
                any(EvidenceArchiveStatus.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        verify(storageService, never()).removeEvidenceObject(
                any(String.class),
                any(String.class)
        );
        verify(auditService, never()).recordPurge(
                any(String.class),
                any(String.class),
                any(Integer.class),
                any()
        );
    }

    @Test
    void shouldReleaseCandidateWhenArchiveVerificationFails() {
        EvidenceAsset candidate = purgeCandidate();
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-001");
        stubCandidate(candidate);
        when(assetRepository.claimPurge(
                eq(1L),
                eq(EvidenceArchiveStatus.ARCHIVED),
                eq(EvidenceArchiveStatus.PURGING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(jobRepository.findByJobCode("AR-001"))
                .thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("归档包 SHA-256 校验失败"))
                .when(integrityService)
                .verifyBeforePurge(job);

        EvidenceCleanupBatchResponse result = service.runBatch(false, 1);

        // 完整性不可信时，一个 MinIO 删除调用都不能发生。
        verify(storageService, never()).removeEvidenceObject(
                any(String.class),
                any(String.class)
        );
        assertThat(candidate.getArchiveStatus())
                .isEqualTo(EvidenceArchiveStatus.ARCHIVED);
        assertThat(candidate.getPurgeError())
                .isEqualTo("归档包 SHA-256 校验失败");
        assertThat(result.purged()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    private void stubCandidate(EvidenceAsset candidate) {
        // 预览和正式执行都会先取得总数和当前页候选。
        when(assetRepository.countPurgeCandidates(
                eq(EvidenceArchiveStatus.ARCHIVED),
                any(LocalDateTime.class)
        )).thenReturn(1L);
        when(assetRepository.findPurgeCandidates(
                eq(EvidenceArchiveStatus.ARCHIVED),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(candidate));
    }

    private static EvidenceAsset purgeCandidate() {
        // 候选具备软删除、归档批次、哈希和三个待删除对象键。
        EvidenceAsset asset = new EvidenceAsset();
        asset.setId(1L);
        asset.setEvidenceCode("EV-001");
        asset.setBucket("evidence");
        asset.setObjectKey("TASK-001/source.jpg");
        asset.setThumbnailObjectKey("derivatives/EV-001-thumb.jpg");
        asset.setPosterObjectKey("derivatives/EV-001-poster.jpg");
        asset.setDeleted(true);
        asset.setArchiveStatus(EvidenceArchiveStatus.ARCHIVED);
        asset.setArchiveBatchCode("AR-001");
        asset.setContentHash("sha256:evidence");
        return asset;
    }
}
