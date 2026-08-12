package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceArchiveIntegrityServiceTest {

    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceHashService hashService =
            mock(EvidenceHashService.class);
    private final EvidenceArchiveJobRepository repository =
            mock(EvidenceArchiveJobRepository.class);
    private final EvidenceArchiveIntegrityService service =
            new EvidenceArchiveIntegrityService(
                    storageService,
                    hashService,
                    repository,
                    new ObjectMapper()
            );

    @Test
    void shouldVerifyArchiveObjectsAndPersistVerificationTime() {
        // 构造一个拥有完整 ZIP、manifest 和期望哈希的已完成任务。
        EvidenceArchiveJob job = completedJob();
        byte[] manifestBytes = validManifestBytes();
        when(storageService.objectExists(
                "evidence",
                "archives/AR-001/AR-001.zip"
        )).thenReturn(true);
        when(storageService.objectExists(
                "evidence",
                "archives/AR-001/manifest.json"
        )).thenReturn(true);
        ByteArrayInputStream hashStream = new ByteArrayInputStream(
                "archive-content".getBytes(StandardCharsets.UTF_8)
        );
        when(storageService.getObjectStream(
                "evidence",
                "archives/AR-001/AR-001.zip"
        )).thenReturn(
                hashStream,
                new ByteArrayInputStream(zipWithManifest(manifestBytes))
        );
        when(storageService.getObjectStream(
                "evidence",
                "archives/AR-001/manifest.json"
        )).thenReturn(new ByteArrayInputStream(manifestBytes));
        when(hashService.sha256Hex(hashStream))
                .thenReturn("sha256:expected");

        // 首次清理前执行完整对象存在性和字节哈希校验。
        EvidenceArchiveIntegrityService.VerifiedArchiveManifest manifest =
                service.verifyBeforePurge(job);
        EvidenceAsset asset = archivedAsset(
                "EV-001",
                "sha256:evidence",
                15L
        );
        // 包级校验结果还必须能够证明这条具体证据已进入归档。
        service.verifyContains(manifest, asset);

        assertThat(job.getPackageVerifiedAt()).isNotNull();
        assertThat(manifest.filesByEvidenceCode()).containsKey("EV-001");
        verify(repository).save(job);
    }

    @Test
    void shouldRejectHashMismatchWithoutMarkingJobVerified() {
        EvidenceArchiveJob job = completedJob();
        when(storageService.objectExists(
                "evidence",
                "archives/AR-001/AR-001.zip"
        )).thenReturn(true);
        when(storageService.objectExists(
                "evidence",
                "archives/AR-001/manifest.json"
        )).thenReturn(true);
        ByteArrayInputStream archiveStream = new ByteArrayInputStream(
                "tampered".getBytes(StandardCharsets.UTF_8)
        );
        when(storageService.getObjectStream(
                "evidence",
                "archives/AR-001/AR-001.zip"
        )).thenReturn(archiveStream);
        when(hashService.sha256Hex(archiveStream))
                .thenReturn("sha256:different");

        // MinIO 中的 ZIP 与打包时摘要不同，必须阻断物理清理。
        assertThatThrownBy(() -> service.verifyBeforePurge(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("归档包 SHA-256 校验失败");
        assertThat(job.getPackageVerifiedAt()).isNull();
        verify(repository, never()).save(job);
    }

    @Test
    void shouldRejectLegacyJobWithoutPackageHash() {
        // 老版本归档任务没有可信摘要，不能事后拿当前对象给自己建立信任。
        EvidenceArchiveJob job = completedJob();
        job.setPackageContentHash(null);

        assertThatThrownBy(() -> service.verifyBeforePurge(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("归档任务缺少归档包 SHA-256");
        verify(storageService, never()).getObjectStream(
                "evidence",
                "archives/AR-001/AR-001.zip"
        );
    }

    @Test
    void shouldRejectCandidateWhoseHashDiffersFromManifest() {
        // 即使归档包整体可信，当前数据库记录也必须与包内条目保持一致。
        EvidenceArchiveIntegrityService.VerifiedArchiveManifest manifest =
                new EvidenceArchiveIntegrityService.VerifiedArchiveManifest(
                        "AR-001",
                        Map.of(
                                "EV-001",
                                new EvidenceArchiveIntegrityService.ManifestEvidence(
                                        "sha256:archived",
                                        15L,
                                        "files/EV-001.jpg"
                                )
                        )
                );
        EvidenceAsset candidate = archivedAsset(
                "EV-001",
                "sha256:current",
                15L
        );

        assertThatThrownBy(() -> service.verifyContains(manifest, candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("归档 manifest 内容哈希不一致: EV-001");
    }

    private static EvidenceArchiveJob completedJob() {
        // 所有测试从同一个最小有效归档任务开始，再覆盖单个失败条件。
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode("AR-001");
        job.setStatus(EvidenceArchiveJobStatus.COMPLETED);
        job.setOutputBucket("evidence");
        job.setOutputObjectKey("archives/AR-001/AR-001.zip");
        job.setManifestObjectKey("archives/AR-001/manifest.json");
        job.setPackageContentHash("sha256:expected");
        job.setTotalFiles(1);
        job.setTotalBytes(15L);
        return job;
    }

    private static EvidenceAsset archivedAsset(
            String evidenceCode,
            String contentHash,
            long sizeBytes) {
        // 候选字段与 manifest 中用于删除门禁的三个一致性字段对应。
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode(evidenceCode);
        asset.setContentHash(contentHash);
        asset.setSizeBytes(sizeBytes);
        return asset;
    }

    private static byte[] validManifestBytes() {
        // 使用与生产 EvidenceManifestService 相同的字段结构构造最小可信清单。
        return """
                {
                  "jobCode": "AR-001",
                  "totalFiles": 1,
                  "totalBytes": 15,
                  "files": [{
                    "evidenceCode": "EV-001",
                    "archivePath": "files/EV-001.jpg",
                    "sizeBytes": 15,
                    "contentHash": "sha256:evidence"
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zipWithManifest(byte[] manifestBytes) {
        // manifest 必须位于 ZIP 首项，模拟生产打包服务的稳定顺序。
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("测试 ZIP 构造失败", exception);
        }
    }
}
