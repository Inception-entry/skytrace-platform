package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    private final EvidenceDerivativeJobService derivativeJobService =
            mock(EvidenceDerivativeJobService.class);
    private EvidenceCommandService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceCommandService(
                repository,
                storageService,
                actorContextService,
                accessLogService,
                queryService,
                derivativeJobService
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
}