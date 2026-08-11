package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceAccessService {

    private final EvidenceQueryService queryService;
    private final EvidenceStorageService storageService;
    private final EvidenceAccessLogService accessLogService;

    public EvidenceAccessService(
            EvidenceQueryService queryService,
            EvidenceStorageService storageService,
            EvidenceAccessLogService accessLogService) {
        this.queryService = queryService;
        this.storageService = storageService;
        this.accessLogService = accessLogService;
    }

    @Transactional
    public EvidenceAccessUrlResponse createPreviewUrl(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        accessLogService.recordPreview(asset);
        int ttl = storageService.previewTtlSeconds();
        String url = storageService.createPresignedGetUrl(
                asset.getBucket(),
                asset.getObjectKey(),
                ttl,
                null
        );
        return new EvidenceAccessUrlResponse(
                url,
                storageService.expiresAt(ttl)
        );
    }

    @Transactional
    public EvidenceAccessUrlResponse createDownloadUrl(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        accessLogService.recordDownload(asset);
        int ttl = storageService.downloadTtlSeconds();
        String filename = asset.getOriginalFilename() == null
                ? asset.getEvidenceCode()
                : asset.getOriginalFilename().replace("\"", "");
        String disposition = "attachment; filename=\"" + filename + "\"";
        String url = storageService.createPresignedGetUrl(
                asset.getBucket(),
                asset.getObjectKey(),
                ttl,
                disposition
        );
        return new EvidenceAccessUrlResponse(
                url,
                storageService.expiresAt(ttl)
        );
    }
}