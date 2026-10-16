package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.domain.EvidenceObjectStatus;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceUploadResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceCommandService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCommandService.class);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;
    private final EvidenceActorContextService actorContextService;
    private final EvidenceAccessLogService accessLogService;
    private final EvidenceQueryService queryService;
    private final EvidenceOutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;

    public EvidenceCommandService(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService,
            EvidenceActorContextService actorContextService,
            EvidenceAccessLogService accessLogService,
            EvidenceQueryService queryService,
            EvidenceOutboxWriter outboxWriter,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storageService = storageService;
        this.actorContextService = actorContextService;
        this.accessLogService = accessLogService;
        this.queryService = queryService;
        this.outboxWriter = outboxWriter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public EvidenceUploadResponse upload(
            MultipartFile file,
            String taskCode,
            String alarmEventCode,
            String deviceCode) {
        String evidenceCode = nextEvidenceCode();
        transactionTemplate.executeWithoutResult(status ->
                reserveUpload(evidenceCode, taskCode, alarmEventCode, deviceCode)
        );
        EvidenceStorageService.StoredObject stored;
        try {
            stored = storageService.store(file, taskCode);
        } catch (RuntimeException exception) {
            markFailed(evidenceCode);
            throw exception;
        }
        try {
            return transactionTemplate.execute(status ->
                    persistUpload(
                            evidenceCode,
                            stored,
                            taskCode,
                            alarmEventCode,
                            deviceCode
                    )
            );
        } catch (RuntimeException exception) {
            compensateStoredObject(stored);
            markFailed(evidenceCode);
            throw exception;
        }
    }

    private void reserveUpload(
            String evidenceCode,
            String taskCode,
            String alarmEventCode,
            String deviceCode) {
        EvidenceActorContext actor = actorContextService.current();
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode(evidenceCode);
        asset.setObjectKey("pending/" + evidenceCode);
        asset.setBucket(storageService.evidenceBucket());
        asset.setObjectStatus(EvidenceObjectStatus.PENDING_UPLOAD);
        asset.setAssetType(EvidenceAssetType.IMAGE);
        asset.setSourceType(EvidenceSourceType.MANUAL_UPLOAD);
        asset.setReviewStatus(EvidenceReviewStatus.PENDING);
        asset.setContentType("application/octet-stream");
        asset.setSizeBytes(0);
        asset.setTaskCode(blankToNull(taskCode));
        asset.setAlarmEventCode(blankToNull(alarmEventCode));
        asset.setDeviceCode(blankToNull(deviceCode));
        asset.setUploadedBy(actor.actorId());
        asset.setUploadedByName(actor.username());
        asset.setDerivativeStatus(EvidenceDerivativeStatus.PENDING);
        repository.save(asset);
    }

    private void markFailed(String evidenceCode) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    repository.findByEvidenceCode(evidenceCode).ifPresent(asset -> {
                        asset.setObjectStatus(EvidenceObjectStatus.FAILED);
                        repository.save(asset);
                    })
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event=evidence_mark_failed evidenceCode={} reason={}",
                    evidenceCode,
                    exception.toString()
            );
        }
    }

    private EvidenceUploadResponse persistUpload(
            String evidenceCode,
            EvidenceStorageService.StoredObject stored,
            String taskCode,
            String alarmEventCode,
            String deviceCode) {
        EvidenceAsset asset = repository.findByEvidenceCode(evidenceCode)
                .orElseThrow(() -> new IllegalStateException(
                        "证据上传预留记录不存在"
                ));
        asset.setObjectKey(stored.objectKey());
        asset.setBucket(stored.bucket());
        asset.setAssetType(stored.assetType());
        asset.setObjectStatus(EvidenceObjectStatus.AVAILABLE);
        asset.setContentType(stored.contentType());
        asset.setOriginalFilename(stored.originalFilename());
        asset.setSizeBytes(stored.sizeBytes());
        asset.setTaskCode(blankToNull(taskCode));
        asset.setAlarmEventCode(blankToNull(alarmEventCode));
        asset.setDeviceCode(blankToNull(deviceCode));
        repository.save(asset);
        accessLogService.recordUpload(asset);
        outboxWriter.enqueueDerivative(asset.getEvidenceCode());

        return new EvidenceUploadResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                storageService.legacyPublicPath(
                        asset.getBucket(),
                        asset.getObjectKey()
                )
        );
    }

    private void compensateStoredObject(EvidenceStorageService.StoredObject stored) {
        try {
            storageService.removeEvidenceObject(stored.bucket(), stored.objectKey());
        } catch (RuntimeException deleteException) {
            try {
                outboxWriter.recordOrphanDelete(stored.bucket(), stored.objectKey());
            } catch (RuntimeException outboxException) {
                log.warn(
                        "event=evidence_minio_orphan bucket={} objectKey={} deleteReason={} outboxReason={}",
                        stored.bucket(),
                        stored.objectKey(),
                        deleteException.toString(),
                        outboxException.toString()
                );
            }
        }
    }

    @Transactional
    public void softDelete(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        EvidenceActorContext actor = actorContextService.current();
        asset.setDeleted(true);
        asset.setDeletedAt(LocalDateTime.now());
        asset.setDeletedBy(actor.actorId());
        asset.setDeletedByName(actor.username());
        repository.save(asset);
        accessLogService.recordDelete(asset);
    }

    @Transactional
    public void restore(String evidenceCode) {
        EvidenceAsset asset = queryService.requireAny(evidenceCode);
        if (!asset.isDeleted()) {
            throw new IllegalArgumentException("证据未被删除，无需恢复");
        }
        // PURGING 已被清理任务原子认领，恢复会与对象删除形成竞态，必须先拒绝。
        if (asset.getArchiveStatus() == EvidenceArchiveStatus.PURGING) {
            throw new IllegalStateException("证据正在物理清理，暂时不能恢复");
        }
        // PURGED 只保留数据库墓碑，原始对象已经不存在，不能恢复成可访问证据。
        if (asset.getArchiveStatus() == EvidenceArchiveStatus.PURGED) {
            throw new IllegalStateException("证据内容已物理清理，不能恢复");
        }
        asset.setDeleted(false);
        asset.setDeletedAt(null);
        asset.setDeletedBy(null);
        asset.setDeletedByName(null);
        repository.save(asset);
        accessLogService.recordRestore(asset);
    }

    private String nextEvidenceCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "EV-" + day + "-" + suffix;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
