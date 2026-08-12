package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceHashService {

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;

    public EvidenceHashService(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    public String ensureContentHash(EvidenceAsset asset) {
        if (asset.getContentHash() != null && !asset.getContentHash().isBlank()) {
            return asset.getContentHash();
        }
        try (InputStream inputStream = storageService.getObjectStream(
                asset.getBucket(),
                asset.getObjectKey()
        )) {
            String contentHash = sha256Hex(inputStream);
            asset.setContentHash(contentHash);
            repository.save(asset);
            return contentHash;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "计算证据内容哈希失败: " + asset.getEvidenceCode(),
                    exception
            );
        }
    }

    public String sha256Hex(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream stream =
                         new DigestInputStream(inputStream, digest)) {
                stream.transferTo(OutputStream.nullOutputStream());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("计算内容哈希失败", exception);
        }
    }
}
