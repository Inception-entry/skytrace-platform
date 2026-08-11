package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceRegistrationServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceDerivativeJobService derivativeJobService =
            mock(EvidenceDerivativeJobService.class);
    private EvidenceRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceRegistrationService(
                repository,
                derivativeJobService
        );
        when(repository.save(any(EvidenceAsset.class))).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        when(repository.findByObjectKey(any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldRegisterAiEvidenceAndStartDerivative() {
        EvidenceAsset asset = service.register(
                new EvidenceRegistrationService.RegisterCommand(
                        "ai/demo.jpg",
                        "skytrace-evidence",
                        "image/jpeg",
                        "demo.jpg",
                        100L,
                        EvidenceSourceType.AI_DETECTION,
                        "TASK-1",
                        "ALARM-1",
                        "UAV-1",
                        "analysis-1",
                        "system",
                        "ai"
                )
        );

        assertThat(asset.getEvidenceCode()).startsWith("EV-");
        assertThat(asset.getDerivativeStatus())
                .isEqualTo(EvidenceDerivativeStatus.PENDING);
        assertThat(asset.getSourceType())
                .isEqualTo(EvidenceSourceType.AI_DETECTION);
        verify(derivativeJobService).start(asset.getEvidenceCode());
    }

    @Test
    void shouldReuseExistingObjectKeyWithoutStartingDerivativeAgain() {
        EvidenceAsset existing = new EvidenceAsset();
        existing.setEvidenceCode("EV-EXISTING");
        existing.setObjectKey("tasks/demo.png");
        existing.setSourceType(EvidenceSourceType.MANUAL_UPLOAD);
        when(repository.findByObjectKey("tasks/demo.png"))
                .thenReturn(Optional.of(existing));

        EvidenceAsset asset = service.register(
                new EvidenceRegistrationService.RegisterCommand(
                        "tasks/demo.png",
                        "skytrace-evidence",
                        "image/png",
                        "demo.png",
                        10L,
                        EvidenceSourceType.AI_DETECTION,
                        "TASK-1",
                        null,
                        "UAV-1",
                        null,
                        "system",
                        "ai"
                )
        );

        assertThat(asset.getEvidenceCode()).isEqualTo("EV-EXISTING");
        assertThat(asset.getTaskCode()).isEqualTo("TASK-1");
        assertThat(asset.getDeviceCode()).isEqualTo("UAV-1");
        assertThat(asset.getSourceType())
                .isEqualTo(EvidenceSourceType.MANUAL_UPLOAD);
        verify(derivativeJobService, never()).start(any());
    }
}
