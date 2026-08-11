package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.MinioProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EvidenceStorageServiceTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioProperties properties = new MinioProperties();
    private EvidenceStorageService service;

    @BeforeEach
    void setUp() {
        properties.setEvidenceBucket("evidence");
        service = new EvidenceStorageService(minioClient, properties);
    }

    @Test
    void shouldBuildLegacyPublicPath() {
        assertThat(service.legacyPublicPath("evidence", "TASK-001/demo.jpg"))
                .isEqualTo("/files/evidence/TASK-001/demo.jpg");
    }

    @Test
    void shouldExposeConfiguredTtl() {
        properties.setPresignPreviewTtlSeconds(120);
        properties.setPresignDownloadTtlSeconds(180);
        assertThat(service.previewTtlSeconds()).isEqualTo(120);
        assertThat(service.downloadTtlSeconds()).isEqualTo(180);
    }
}
