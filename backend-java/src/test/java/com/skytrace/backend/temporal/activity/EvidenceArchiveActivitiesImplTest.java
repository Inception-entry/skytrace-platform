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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceArchiveActivitiesImplTest {

    @Test
    void shouldCompleteArchiveJobAndMarkAssetsArchived() {
        EvidenceArchiveJobRepository jobRepository =
                mock(EvidenceArchiveJobRepository.class);
        EvidenceAssetRepository assetRepository =
                mock(EvidenceAssetRepository.class);
        EvidenceHashService hashService = mock(EvidenceHashService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceHashService> hashProvider = mock(ObjectProvider.class);
        EvidenceManifestService manifestService = mock(EvidenceManifestService.class);
        EvidenceArchivePackageService archivePackageService =
                mock(EvidenceArchivePackageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceArchivePackageService> packageProvider =
                mock(ObjectProvider.class);
        when(hashProvider.getIfAvailable()).thenReturn(hashService);
        when(packageProvider.getIfAvailable()).thenReturn(archivePackageService);
        EvidenceArchiveActivitiesImpl activities =
                new EvidenceArchiveActivitiesImpl(
                        jobRepository,
                        assetRepository,
                        hashProvider,
                        manifestService,
                        packageProvider
                );

        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-20260811-ABC123");
        job.setScopeType(EvidenceArchiveScopeType.TASK);
        job.setScopeValue("TASK-001");
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-001");
        asset.setBucket("skytrace-evidence");
        asset.setObjectKey("TASK-001/demo.jpg");
        asset.setSizeBytes(128L);

        List<EvidenceManifestService.ArchivedEvidenceFile> files = List.of(
                new EvidenceManifestService.ArchivedEvidenceFile(
                        "EV-001",
                        "demo.jpg",
                        "image/jpeg",
                        128L,
                        "sha256:abc",
                        "TASK-001",
                        null,
                        "skytrace-evidence",
                        "TASK-001/demo.jpg",
                        "files/EV-001.jpg"
                )
        );

        when(jobRepository.findByJobCode("AR-20260811-ABC123"))
                .thenReturn(Optional.of(job));
        when(assetRepository.findByTaskCodeAndDeletedFalseOrderByCreatedAtAsc(
                "TASK-001"
        )).thenReturn(List.of(asset));
        when(manifestService.describe(List.of(asset))).thenReturn(files);
        when(manifestService.buildManifest(job, files))
                .thenReturn("manifest".getBytes());
        when(manifestService.buildChecksums(files))
                .thenReturn("checksums".getBytes());
        when(archivePackageService.buildAndStore(
                job,
                files,
                "manifest".getBytes(),
                "checksums".getBytes()
        )).thenReturn(new EvidenceArchivePackageService.ArchivePackageResult(
                "skytrace-evidence",
                "archives/AR-20260811-ABC123/AR-20260811-ABC123.zip",
                "archives/AR-20260811-ABC123/manifest.json",
                1,
                128L
        ));

        activities.executeArchive("AR-20260811-ABC123");

        verify(hashService).ensureContentHash(asset);
        verify(assetRepository).saveAll(List.of(asset));
        verify(jobRepository).save(job);
        assertThat(asset.getArchiveStatus()).isEqualTo(EvidenceArchiveStatus.ARCHIVED);
        assertThat(asset.getArchiveBatchCode()).isEqualTo("AR-20260811-ABC123");
        assertThat(asset.getArchivedAt()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(EvidenceArchiveJobStatus.COMPLETED);
        assertThat(job.getOutputBucket()).isEqualTo("skytrace-evidence");
        assertThat(job.getTotalFiles()).isEqualTo(1);
        assertThat(job.getTotalBytes()).isEqualTo(128L);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldMarkArchiveJobFailed() {
        EvidenceArchiveJobRepository jobRepository =
                mock(EvidenceArchiveJobRepository.class);
        EvidenceAssetRepository assetRepository =
                mock(EvidenceAssetRepository.class);
        EvidenceHashService hashService = mock(EvidenceHashService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceHashService> hashProvider = mock(ObjectProvider.class);
        EvidenceManifestService manifestService = mock(EvidenceManifestService.class);
        EvidenceArchivePackageService archivePackageService =
                mock(EvidenceArchivePackageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceArchivePackageService> packageProvider =
                mock(ObjectProvider.class);
        EvidenceArchiveActivitiesImpl activities =
                new EvidenceArchiveActivitiesImpl(
                        jobRepository,
                        assetRepository,
                        hashProvider,
                        manifestService,
                        packageProvider
                );

        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-20260811-FAILED");
        when(jobRepository.findByJobCode("AR-20260811-FAILED"))
                .thenReturn(Optional.of(job));

        activities.markFailed("AR-20260811-FAILED", "boom");

        verify(jobRepository).save(job);
        assertThat(job.getStatus()).isEqualTo(EvidenceArchiveJobStatus.FAILED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getErrorMessage()).isEqualTo("boom");
    }
}
