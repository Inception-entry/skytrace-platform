package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceQueryServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceTagService tagService = mock(EvidenceTagService.class);
    private EvidenceQueryService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceQueryService(repository, storageService, tagService);
        when(storageService.legacyPublicPath("evidence", "TASK-001/demo.jpg"))
                .thenReturn("/files/evidence/TASK-001/demo.jpg");
    }

    @Test
    void shouldRequireFilterWhenListingEvidence() {
        assertThatThrownBy(() -> service.findLegacy(null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少提供");
    }

    @Test
    void shouldListEvidenceByTaskCode() {
        EvidenceAsset asset = sample();
        when(repository.findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(
                "TASK-001"
        )).thenReturn(List.of(asset));

        List<EvidenceAssetResponse> responses =
                service.findLegacy("TASK-001", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().evidenceCode())
                .isEqualTo("EV-20260810-DEMO0001");
        assertThat(responses.getFirst().taskCode()).isEqualTo("TASK-001");
    }

    private EvidenceAsset sample() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-20260810-DEMO0001");
        asset.setObjectKey("TASK-001/demo.jpg");
        asset.setBucket("evidence");
        asset.setAssetType(EvidenceAssetType.IMAGE);
        asset.setSourceType(EvidenceSourceType.MANUAL_UPLOAD);
        asset.setContentType("image/jpeg");
        asset.setOriginalFilename("demo.jpg");
        asset.setSizeBytes(128);
        asset.setTaskCode("TASK-001");
        return asset;
    }
}
