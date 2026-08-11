package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.dto.BatchReviewEvidenceRequest;
import com.skytrace.backend.evidence.dto.BatchTagEvidenceRequest;
import com.skytrace.backend.evidence.dto.UpdateEvidenceMetadataRequest;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceMetadataService {

    private final EvidenceQueryService queryService;
    private final EvidenceAssetRepository repository;
    private final EvidenceTagService tagService;
    private final EvidenceActorContextService actorContextService;

    public EvidenceMetadataService(
            EvidenceQueryService queryService,
            EvidenceAssetRepository repository,
            EvidenceTagService tagService,
            EvidenceActorContextService actorContextService) {
        this.queryService = queryService;
        this.repository = repository;
        this.tagService = tagService;
        this.actorContextService = actorContextService;
    }

    @Transactional
    public void updateMetadata(
            String evidenceCode,
            UpdateEvidenceMetadataRequest request) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        if (request.remark() != null) {
            asset.setRemark(blankToNull(request.remark()));
        }
        if (request.reviewStatus() != null) {
            applyReview(asset, request.reviewStatus(), request.reviewComment());
        }
        if (request.tagIds() != null) {
            tagService.replaceTags(asset.getId(), request.tagIds());
        }
        repository.save(asset);
    }

    @Transactional
    public void batchReview(BatchReviewEvidenceRequest request) {
        if (request.evidenceCodes() == null || request.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("evidenceCodes 不能为空");
        }
        for (String code : request.evidenceCodes()) {
            EvidenceAsset asset = queryService.requireActive(code);
            applyReview(asset, request.reviewStatus(), request.reviewComment());
            repository.save(asset);
        }
    }

    @Transactional
    public void batchTags(BatchTagEvidenceRequest request) {
        if (request.evidenceCodes() == null || request.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("evidenceCodes 不能为空");
        }
        for (String code : request.evidenceCodes()) {
            EvidenceAsset asset = queryService.requireActive(code);
            if (request.replace()) {
                tagService.replaceTags(asset.getId(), request.tagIds());
            } else {
                tagService.addTags(asset.getId(), request.tagIds());
            }
        }
    }

    private void applyReview(
            EvidenceAsset asset,
            String reviewStatus,
            String reviewComment) {
        EvidenceReviewStatus status = EvidenceReviewStatus.valueOf(
                reviewStatus.trim().toUpperCase(Locale.ROOT)
        );
        EvidenceActorContext actor = actorContextService.current();
        asset.setReviewStatus(status);
        asset.setReviewComment(blankToNull(reviewComment));
        asset.setReviewedAt(LocalDateTime.now());
        asset.setReviewedBy(actor.actorId());
        asset.setReviewedByName(actor.username());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
