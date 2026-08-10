package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import com.skytrace.backend.evidence.service.EvidenceStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.NoSuchElementException;

@Component("evidenceDerivativeActivities")
@ConditionalOnBean(EvidenceStorageService.class)
public class EvidenceDerivativeActivitiesImpl
        implements EvidenceDerivativeActivities {

    private static final int THUMB_MAX = 320;

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;

    public EvidenceDerivativeActivitiesImpl(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @Override
    public void generateDerivatives(String evidenceCode) {
        EvidenceAsset asset = repository.findByEvidenceCode(evidenceCode)
                .orElseThrow(() -> new NoSuchElementException(evidenceCode));
        try {
            if (asset.getAssetType() == EvidenceAssetType.IMAGE) {
                byte[] original = storageService.getObjectBytes(
                        asset.getBucket(),
                        asset.getObjectKey()
                );
                byte[] thumb = createThumbnail(original);
                String thumbKey = "derivatives/" + evidenceCode + "/thumb.jpg";
                storageService.putObject(
                        asset.getBucket(),
                        thumbKey,
                        thumb,
                        "image/jpeg"
                );
                asset.setThumbnailObjectKey(thumbKey);
            } else {
                // 视频封面依赖外部抽帧；无 ffmpeg 时标记 READY，前端用占位。
                asset.setPosterObjectKey(null);
            }
            asset.setDerivativeStatus(EvidenceDerivativeStatus.READY);
            repository.save(asset);
        } catch (Exception ex) {
            asset.setDerivativeStatus(EvidenceDerivativeStatus.FAILED);
            repository.save(asset);
            throw ex instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(ex);
        }
    }

    private static byte[] createThumbnail(byte[] original) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
        if (source == null) {
            throw new IllegalArgumentException("无法解析图片");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(
                1.0,
                (double) THUMB_MAX / Math.max(width, height)
        );
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));
        Image scaled = source.getScaledInstance(
                targetW,
                targetH,
                Image.SCALE_SMOOTH
        );
        BufferedImage thumb = new BufferedImage(
                targetW,
                targetH,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = thumb.createGraphics();
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", output);
        return output.toByteArray();
    }
}
