package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import com.skytrace.backend.evidence.service.EvidenceStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceDerivativeActivitiesImplTest {

    @Test
    void shouldGenerateImageThumbnail() throws Exception {
        EvidenceAssetRepository repository = mock(EvidenceAssetRepository.class);
        EvidenceStorageService storageService = mock(EvidenceStorageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceStorageService> storageProvider = mock(ObjectProvider.class);
        when(storageProvider.getIfAvailable()).thenReturn(storageService);
        EvidenceDerivativeActivitiesImpl activities =
                new EvidenceDerivativeActivitiesImpl(repository, storageProvider);

        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        asset.setBucket("bucket");
        asset.setObjectKey("a.jpg");
        asset.setAssetType(EvidenceAssetType.IMAGE);
        when(repository.findByEvidenceCode("EV-1")).thenReturn(Optional.of(asset));
        when(storageService.getObjectBytes("bucket", "a.jpg"))
                .thenReturn(tinyJpeg());

        activities.generateDerivatives("EV-1");

        assertThat(asset.getDerivativeStatus())
                .isEqualTo(EvidenceDerivativeStatus.READY);
        assertThat(asset.getThumbnailObjectKey())
                .isEqualTo("derivatives/EV-1/thumb.jpg");
        verify(storageService).putObject(
                eq("bucket"),
                eq("derivatives/EV-1/thumb.jpg"),
                any(byte[].class),
                eq("image/jpeg")
        );
        verify(repository).save(asset);
    }

    private static byte[] tinyJpeg() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
