package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceAccessServiceTest {

    private final EvidenceQueryService queryService =
            mock(EvidenceQueryService.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceAccessLogService accessLogService =
            mock(EvidenceAccessLogService.class);
    private EvidenceAccessService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceAccessService(
                queryService,
                storageService,
                accessLogService
        );
    }

    @Test
    void shouldCreatePreviewUrlAndLog() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        asset.setBucket("b");
        asset.setObjectKey("k");
        when(queryService.requireActive("EV-1")).thenReturn(asset);
        when(storageService.previewTtlSeconds()).thenReturn(300);
        when(storageService.createPresignedGetUrl("b", "k", 300, null))
                .thenReturn("http://signed");
        Instant expires = Instant.parse("2026-08-10T09:16:43Z");
        when(storageService.expiresAt(300)).thenReturn(expires);

        EvidenceAccessUrlResponse response =
                service.createPreviewUrl("EV-1");

        assertThat(response.url()).isEqualTo("http://signed");
        assertThat(response.expiresAt()).isEqualTo(expires);
        verify(accessLogService).recordPreview(asset);
    }
}