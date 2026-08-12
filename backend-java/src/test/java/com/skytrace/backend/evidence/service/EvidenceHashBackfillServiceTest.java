package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceHashBackfillResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceHashBackfillServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceHashService hashService =
            mock(EvidenceHashService.class);
    private final EvidenceMaintenanceProperties properties =
            new EvidenceMaintenanceProperties();
    private EvidenceHashBackfillService service;

    @BeforeEach
    void setUp() {
        // 固定配置让候选查询和批次统计在测试中保持可预测。
        properties.setHashBackfillBatchSize(25);
        properties.setHashBackfillRetryHours(24);
        service = new EvidenceHashBackfillService(
                repository,
                hashService,
                properties
        );
    }

    @Test
    void shouldContinueBatchWhenOneHistoricalObjectFails() {
        // 第一条模拟成功回填，第二条模拟 MinIO 读取失败。
        EvidenceAsset successful = asset(1L, "EV-001");
        EvidenceAsset failed = asset(2L, "EV-002");
        when(repository.findHashBackfillCandidates(
                eq(EvidenceArchiveStatus.PURGED),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(successful, failed));
        // 两条记录都成功完成数据库原子认领。
        when(repository.claimHashBackfill(
                any(Long.class),
                eq(EvidenceArchiveStatus.PURGED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        // 真实哈希服务会修改实体，Mock 在这里复现同样副作用。
        when(hashService.ensureContentHash(successful)).thenAnswer(invocation -> {
            successful.setContentHash("sha256:abc");
            return "sha256:abc";
        });
        when(hashService.ensureContentHash(failed))
                .thenThrow(new IllegalStateException(
                        "计算证据内容哈希失败",
                        new RuntimeException("MinIO connection reset")
                ));

        // 执行一轮历史数据回填。
        EvidenceHashBackfillResponse result = service.runBatch(null);

        // 单条失败不能阻止成功记录提交，也不能让整批统计失真。
        assertThat(result.selected()).isEqualTo(2);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedEvidenceCodes()).containsExactly("EV-002");
        // 最底层异常写入记录，管理员能看到真正的 MinIO 原因。
        assertThat(failed.getHashBackfillError())
                .isEqualTo("MinIO connection reset");
        assertThat(failed.getHashBackfillAttemptedAt()).isNotNull();
        verify(repository).save(successful);
        verify(repository).save(failed);
    }

    @Test
    void shouldSkipCandidateClaimedByAnotherInstance() {
        // 候选查询与原子 UPDATE 之间可能被另一个应用实例先认领。
        EvidenceAsset candidate = asset(3L, "EV-003");
        when(repository.findHashBackfillCandidates(
                eq(EvidenceArchiveStatus.PURGED),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(candidate));
        when(repository.claimHashBackfill(
                eq(3L),
                eq(EvidenceArchiveStatus.PURGED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(0);

        EvidenceHashBackfillResponse result = service.runBatch(1);

        // 没有取得认领时不能下载对象或计入成功/失败。
        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.claimed()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isZero();
    }

    private static EvidenceAsset asset(Long id, String evidenceCode) {
        // 最小实体只需要主键和业务编号即可覆盖批处理编排。
        EvidenceAsset asset = new EvidenceAsset();
        asset.setId(id);
        asset.setEvidenceCode(evidenceCode);
        return asset;
    }
}
