package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.dto.EvidenceDetailResponse;
import com.skytrace.backend.evidence.dto.EvidencePageResponse;
import com.skytrace.backend.evidence.dto.EvidenceSearchRequest;
import com.skytrace.backend.evidence.dto.EvidenceSummaryResponse;
import com.skytrace.backend.evidence.dto.EvidenceTagResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class EvidenceQueryService {

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;
    private final EvidenceTagService tagService;

    public EvidenceQueryService(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService,
            EvidenceTagService tagService) {
        this.repository = repository;
        this.storageService = storageService;
        this.tagService = tagService;
    }

    public List<EvidenceAssetResponse> findLegacy(
            String taskCode,
            String alarmEventCode) {
        String task = blankToNull(taskCode);
        String alarm = blankToNull(alarmEventCode);
        if (task == null && alarm == null) {
            throw new IllegalArgumentException(
                    "请至少提供 taskCode 或 alarmEventCode"
            );
        }

        List<EvidenceAsset> assets;
        if (task != null && alarm != null) {
            assets = repository
                    .findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                            task,
                            alarm
                    );
        } else if (task != null) {
            assets = repository
                    .findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(task);
        } else {
            assets = repository
                    .findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                            alarm
                    );
        }
        return assets.stream().map(this::toLegacyResponse).toList();
    }

    public EvidencePageResponse search(EvidenceSearchRequest request) {
        int page = request.page() == null ? 0 : Math.max(request.page(), 0);
        int size = request.size() == null
                ? 20
                : Math.min(Math.max(request.size(), 1), 100);
        boolean includeDeleted = Boolean.TRUE.equals(request.includeDeleted());

        Page<EvidenceAsset> result = repository.findAll(
                buildSpec(request, includeDeleted),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        return new EvidencePageResponse(
                result.getContent().stream()
                        .map(this::toSummary)
                        .toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public EvidenceDetailResponse detail(String evidenceCode) {
        EvidenceAsset asset = requireActive(evidenceCode);
        return toDetail(asset);
    }

    public EvidenceAsset requireActive(String evidenceCode) {
        EvidenceAsset asset = requireAny(evidenceCode);
        if (asset.isDeleted()) {
            throw new NoSuchElementException("证据不存在或已删除");
        }
        return asset;
    }

    public EvidenceAsset requireAny(String evidenceCode) {
        String code = blankToNull(evidenceCode);
        if (code == null) {
            throw new IllegalArgumentException("evidenceCode 不能为空");
        }
        return repository.findByEvidenceCode(code)
                .orElseThrow(() -> new NoSuchElementException(
                        "证据不存在：" + code
                ));
    }

    private Specification<EvidenceAsset> buildSpec(
            EvidenceSearchRequest request,
            boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(cb.isFalse(root.get("deleted")));
            }
            String taskCode = blankToNull(request.taskCode());
            if (taskCode != null) {
                predicates.add(cb.equal(root.get("taskCode"), taskCode));
            }
            String alarm = blankToNull(request.alarmEventCode());
            if (alarm != null) {
                predicates.add(cb.equal(root.get("alarmEventCode"), alarm));
            }
            String device = blankToNull(request.deviceCode());
            if (device != null) {
                predicates.add(cb.equal(root.get("deviceCode"), device));
            }
            EvidenceAssetType assetType = parseAssetType(request.assetType());
            if (assetType != null) {
                predicates.add(cb.equal(root.get("assetType"), assetType));
            }
            EvidenceSourceType sourceType = parseSourceType(request.sourceType());
            if (sourceType != null) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            EvidenceReviewStatus reviewStatus =
                    parseReviewStatus(request.reviewStatus());
            if (reviewStatus != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (request.startTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        toLocal(request.startTime())
                ));
            }
            if (request.endTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        toLocal(request.endTime())
                ));
            }
            String keyword = blankToNull(request.keyword());
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("evidenceCode")), like),
                        cb.like(cb.lower(root.get("originalFilename")), like),
                        cb.like(cb.lower(root.get("taskCode")), like),
                        cb.like(cb.lower(root.get("alarmEventCode")), like),
                        cb.like(cb.lower(root.get("deviceCode")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private EvidenceAssetResponse toLegacyResponse(EvidenceAsset asset) {
        return new EvidenceAssetResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getOriginalFilename(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                storageService.legacyPublicPath(
                        asset.getBucket(),
                        asset.getObjectKey()
                ),
                asset.getCreatedAt()
        );
    }

    private EvidenceSummaryResponse toSummary(EvidenceAsset asset) {
        List<EvidenceTagResponse> tags = tagService.tagsOf(asset.getId());
        return new EvidenceSummaryResponse(
                asset.getEvidenceCode(),
                asset.getOriginalFilename(),
                asset.getAssetType().name(),
                asset.getSourceType().name(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                asset.getDeviceCode(),
                asset.getUploadedByName(),
                asset.getSizeBytes(),
                toInstant(asset.getCreatedAt()),
                asset.isDeleted(),
                asset.getReviewStatus() == null
                        ? EvidenceReviewStatus.PENDING.name()
                        : asset.getReviewStatus().name(),
                asset.getContentHash(),
                archiveStatusName(asset),
                tags,
                derivativeUrl(asset.getBucket(), asset.getThumbnailObjectKey()),
                derivativeUrl(asset.getBucket(), asset.getPosterObjectKey())
        );
    }

    private EvidenceDetailResponse toDetail(EvidenceAsset asset) {
        List<EvidenceTagResponse> tags = tagService.tagsOf(asset.getId());
        return new EvidenceDetailResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getAssetType().name(),
                asset.getSourceType().name(),
                asset.getContentType(),
                asset.getOriginalFilename(),
                asset.getSizeBytes(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                asset.getDeviceCode(),
                asset.getUploadedBy(),
                asset.getUploadedByName(),
                toInstant(asset.getCreatedAt()),
                asset.isDeleted(),
                asset.getReviewStatus() == null
                        ? EvidenceReviewStatus.PENDING.name()
                        : asset.getReviewStatus().name(),
                asset.getContentHash(),
                archiveStatusName(asset),
                asset.getReviewComment(),
                asset.getRemark(),
                tags,
                asset.getReviewedByName(),
                asset.getReviewedAt() == null
                        ? null
                        : toInstant(asset.getReviewedAt()),
                asset.getAnalysisId(),
                asset.getDerivativeStatus() == null
                        ? null
                        : asset.getDerivativeStatus().name(),
                asset.getThumbnailObjectKey(),
                asset.getPosterObjectKey(),
                derivativeUrl(asset.getBucket(), asset.getThumbnailObjectKey()),
                derivativeUrl(asset.getBucket(), asset.getPosterObjectKey())
        );
    }

    private String derivativeUrl(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return storageService.createPresignedGetUrl(
                    bucket,
                    objectKey,
                    storageService.previewTtlSeconds(),
                    null
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static EvidenceAssetType parseAssetType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return EvidenceAssetType.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private static EvidenceSourceType parseSourceType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return EvidenceSourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private static EvidenceReviewStatus parseReviewStatus(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return EvidenceReviewStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private static String archiveStatusName(EvidenceAsset asset) {
        EvidenceArchiveStatus status = asset.getArchiveStatus();
        return status == null ? EvidenceArchiveStatus.ACTIVE.name() : status.name();
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
