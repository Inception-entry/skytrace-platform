package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.dto.BatchReviewEvidenceRequest;
import com.skytrace.backend.evidence.dto.UpdateEvidenceMetadataRequest;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceMetadataServiceTest {

    private final EvidenceQueryService queryService =
            mock(EvidenceQueryService.class);
    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceTagService tagService = mock(EvidenceTagService.class);
    private final EvidenceActorContextService actorContextService =
            mock(EvidenceActorContextService.class);
    private EvidenceMetadataService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceMetadataService(
                queryService,
                repository,
                tagService,
                actorContextService
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
    void shouldUpdateReviewStatus() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setId(1L);
        asset.setEvidenceCode("EV-1");
        when(queryService.requireActive("EV-1")).thenReturn(asset);

        service.updateMetadata(
                "EV-1",
                new UpdateEvidenceMetadataRequest(
                        "备注",
                        "APPROVED",
                        "ok",
                        List.of(2L)
                )
        );

        assertThat(asset.getReviewStatus()).isEqualTo(EvidenceReviewStatus.APPROVED);
        assertThat(asset.getRemark()).isEqualTo("备注");
        verify(tagService).replaceTags(1L, List.of(2L));
        verify(repository).save(asset);
    }

    @Test
    void shouldBatchReview() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        when(queryService.requireActive("EV-1")).thenReturn(asset);

        service.batchReview(new BatchReviewEvidenceRequest(
                List.of("EV-1"),
                "REJECTED",
                "误报"
        ));

        assertThat(asset.getReviewStatus()).isEqualTo(EvidenceReviewStatus.REJECTED);
        verify(repository).save(asset);
    }
}
