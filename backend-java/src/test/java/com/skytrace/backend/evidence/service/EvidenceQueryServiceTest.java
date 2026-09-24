package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.dto.EvidenceDetailResponse;
import com.skytrace.backend.evidence.dto.EvidenceSearchRequest;
import com.skytrace.backend.evidence.dto.EvidenceTagResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(tagService.tagsOf(any())).thenReturn(List.of());
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

    @Test
    void detailInstantUsesShanghaiWallClockNotUtc() throws Exception {
        EvidenceAsset asset = sample();
        asset.setCreatedAt(LocalDateTime.of(2026, 8, 24, 16, 0));
        asset.setReviewedAt(LocalDateTime.of(2026, 8, 24, 16, 30));
        when(repository.findByEvidenceCode("EV-20260810-DEMO0001"))
                .thenReturn(Optional.of(asset));

        EvidenceDetailResponse detail = service.detail("EV-20260810-DEMO0001");

        assertThat(detail.createdAt())
                .isEqualTo(Instant.parse("2026-08-24T08:00:00Z"));
        assertThat(detail.reviewedAt())
                .isEqualTo(Instant.parse("2026-08-24T08:30:00Z"));

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertThat(mapper.writeValueAsString(detail.createdAt()))
                .isEqualTo("\"2026-08-24T08:00:00Z\"");
    }

    @Test
    void searchLoadsTagsForTheWholePageAtOnce() {
        EvidenceAsset asset = sample();
        asset.setId(7L);
        asset.setCreatedAt(LocalDateTime.of(2026, 8, 24, 16, 0));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(asset)));
        when(tagService.tagsOfAll(eq(List.of(7L)))).thenReturn(Map.of(
                7L, List.of(new EvidenceTagResponse(1L, "night", "#111111"))
        ));

        var page = service.search(new EvidenceSearchRequest(
                0, 20, "TASK-001", null, null, null, null, null,
                null, null, null, false
        ));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().tags())
                .extracting(EvidenceTagResponse::name)
                .containsExactly("night");
        verify(tagService, never()).tagsOf(any());
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
