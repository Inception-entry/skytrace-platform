package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
