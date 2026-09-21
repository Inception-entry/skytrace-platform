package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceCommandServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceActorContextService actorContextService =
            mock(EvidenceActorContextService.class);
    private final EvidenceAccessLogService accessLogService =
            mock(EvidenceAccessLogService.class);
    private final EvidenceQueryService queryService =
            mock(EvidenceQueryService.class);
    private final EvidenceOutboxWriter outboxWriter =
            mock(EvidenceOutboxWriter.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);
    private EvidenceCommandService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        service = new EvidenceCommandService(
                repository,
                storageService,
                actorContextService,
                accessLogService,
                queryService,
                outboxWriter,
                transactionManager
        );
        when(actorContextService.current()).thenReturn(
                new EvidenceActorContext(
                        "user-1",
                        "operator-a",
                        "OPERATOR",
                        "req-1",
                        "127.0.0.1"
                )
        );
    }

    @Test
    void shouldSoftDeleteEvidence() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        when(queryService.requireActive("EV-1")).thenReturn(asset);

        service.softDelete("EV-1");

        assertThat(asset.isDeleted()).isTrue();
        assertThat(asset.getDeletedBy()).isEqualTo("user-1");
        verify(repository).save(asset);
        verify(accessLogService).recordDelete(asset);
    }

    @Test
    void shouldRestoreEvidence() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        asset.setDeleted(true);
        when(queryService.requireAny("EV-1")).thenReturn(asset);

        service.restore("EV-1");

        assertThat(asset.isDeleted()).isFalse();
        assertThat(asset.getDeletedAt()).isNull();
        verify(accessLogService).recordRestore(asset);
    }

    @Test
    void shouldRejectRestoreAfterPhysicalPurge() {
        // PURGED 记录只剩审计元数据，MinIO 内容已经不存在。
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-PURGED");
        asset.setDeleted(true);
        asset.setArchiveStatus(EvidenceArchiveStatus.PURGED);
        when(queryService.requireAny("EV-PURGED")).thenReturn(asset);

        // 恢复这种记录会制造一个无法预览或下载的“幽灵证据”。
        assertThatThrownBy(() -> service.restore("EV-PURGED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("证据内容已物理清理，不能恢复");
    }

    @Test
    void shouldRejectRestoreWhilePhysicalPurgeIsRunning() {
        // PURGING 表示清理任务已赢得原子认领，原件可能正在被逐个删除。
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-PURGING");
        asset.setDeleted(true);
        asset.setArchiveStatus(EvidenceArchiveStatus.PURGING);
        when(queryService.requireAny("EV-PURGING")).thenReturn(asset);

        // 此时恢复会与 MinIO 删除并发，必须等任务成功或失败释放状态。
        assertThatThrownBy(() -> service.restore("EV-PURGING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("证据正在物理清理，暂时不能恢复");
    }

    @Test
    void uploadStoresOutsideTransactionThenEnqueuesDerivative() {
        stubStoredObject();
        when(repository.save(any(EvidenceAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.legacyPublicPath("skytrace-evidence", "tasks/a.jpg"))
                .thenReturn("/files/skytrace-evidence/tasks/a.jpg");

        var response = service.upload(sampleFile(), "TASK-1", null, "UAV-1");

        assertThat(response.evidenceCode()).startsWith("EV-");
        verify(outboxWriter).enqueueDerivative(response.evidenceCode());
        verify(storageService, never()).removeEvidenceObject(any(), any());
        verify(outboxWriter, never()).recordOrphanDelete(any(), any());
    }

    @Test
    void uploadDeletesObjectWhenPersistFails() {
        stubStoredObject();
        when(repository.save(any(EvidenceAsset.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.upload(sampleFile(), "TASK-1", null, null))
                .hasMessage("db down");

        verify(storageService).removeEvidenceObject("skytrace-evidence", "tasks/a.jpg");
        verify(outboxWriter, never()).enqueueDerivative(any());
        verify(outboxWriter, never()).recordOrphanDelete(any(), any());
    }

    @Test
    void uploadRecordsOrphanWhenPersistAndDeleteFail() {
        stubStoredObject();
        when(repository.save(any(EvidenceAsset.class)))
                .thenThrow(new RuntimeException("db down"));
        doThrow(new RuntimeException("minio down"))
                .when(storageService)
                .removeEvidenceObject("skytrace-evidence", "tasks/a.jpg");

        assertThatThrownBy(() -> service.upload(sampleFile(), "TASK-1", null, null))
                .hasMessage("db down");

        verify(outboxWriter).recordOrphanDelete("skytrace-evidence", "tasks/a.jpg");
    }

    private void stubStoredObject() {
        when(storageService.store(any(), any())).thenReturn(
                new EvidenceStorageService.StoredObject(
                        "tasks/a.jpg",
                        "skytrace-evidence",
                        "image/jpeg",
                        12L,
                        "a.jpg",
                        EvidenceAssetType.IMAGE
                )
        );
    }

    private static MockMultipartFile sampleFile() {
        return new MockMultipartFile(
                "file",
                "a.jpg",
                "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}
        );
    }
}
