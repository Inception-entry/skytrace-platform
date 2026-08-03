package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceStorageServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioProperties properties = new MinioProperties();
    private EvidenceStorageService service;

    @BeforeEach
    void setUp() {
        properties.setEvidenceBucket("evidence");
        service = new EvidenceStorageService(
                minioClient,
                properties,
                repository
        );
    }

    @Test
    void shouldRequireFilterWhenListingEvidence() {
        assertThatThrownBy(() -> service.findEvidence(null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少提供");
    }

    @Test
    void shouldListEvidenceByTaskCode() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setObjectKey("TASK-001/demo.jpg");
        asset.setBucket("evidence");
        asset.setContentType("image/jpeg");
        asset.setOriginalFilename("demo.jpg");
        asset.setSizeBytes(128);
        asset.setTaskCode("TASK-001");
        when(repository.findByTaskCodeOrderByCreatedAtDesc("TASK-001"))
                .thenReturn(List.of(asset));

        List<EvidenceAssetResponse> responses =
                service.findEvidence("TASK-001", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().taskCode()).isEqualTo("TASK-001");
        assertThat(responses.getFirst().publicPath())
                .isEqualTo("/files/evidence/TASK-001/demo.jpg");
        assertThat(responses.getFirst().originalFilename())
                .isEqualTo("demo.jpg");
    }
}
