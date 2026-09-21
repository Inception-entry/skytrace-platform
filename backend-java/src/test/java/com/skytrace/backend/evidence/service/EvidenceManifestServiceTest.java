package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceManifestServiceTest {

    @Test
    void archivePathUsesDetectedContentTypeNotOriginalFilename() {
        EvidenceManifestService service =
                new EvidenceManifestService(new ObjectMapper());
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-001");
        asset.setOriginalFilename("x.php.jpg");
        asset.setContentType("image/jpeg");
        asset.setSizeBytes(12);

        List<EvidenceManifestService.ArchivedEvidenceFile> files =
                service.describe(List.of(asset));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().archivePath()).isEqualTo("files/EV-001.jpg");
    }
}
