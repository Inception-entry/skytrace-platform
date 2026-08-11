package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class EvidenceRegistrationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final EvidenceAssetRepository repository;
    private final EvidenceDerivativeJobService derivativeJobService;

    public EvidenceRegistrationService(
            EvidenceAssetRepository repository,
            EvidenceDerivativeJobService derivativeJobService) {
        this.repository = repository;
        this.derivativeJobService = derivativeJobService;
    }

    public record RegisterCommand(
            String objectKey,
            String bucket,
            String contentType,
            String originalFilename,
            long sizeBytes,
            EvidenceSourceType sourceType,
            String taskCode,
            String alarmEventCode,
            String deviceCode,
            String analysisId,
            String uploadedBy,
            String uploadedByName
    ) {
    }

    @Transactional
    public EvidenceAsset register(RegisterCommand command) {
        // CI / 告警链会先手动上传再投递 detection：同一 objectKey 只登记一次
        return repository.findByObjectKey(command.objectKey())
                .map(existing -> enrichExisting(existing, command))
                .orElseGet(() -> createNew(command));
    }

    private EvidenceAsset enrichExisting(
            EvidenceAsset existing,
            RegisterCommand command) {
        if (blank(existing.getTaskCode()) && !blank(command.taskCode())) {
            existing.setTaskCode(command.taskCode());
        }
        if (blank(existing.getAlarmEventCode())
                && !blank(command.alarmEventCode())) {
            existing.setAlarmEventCode(command.alarmEventCode());
        }
        if (blank(existing.getDeviceCode()) && !blank(command.deviceCode())) {
            existing.setDeviceCode(command.deviceCode());
        }
        if (blank(existing.getAnalysisId()) && !blank(command.analysisId())) {
            existing.setAnalysisId(command.analysisId());
        }
        return repository.save(existing);
    }

    private EvidenceAsset createNew(RegisterCommand command) {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode(nextEvidenceCode());
        asset.setObjectKey(command.objectKey());
        asset.setBucket(command.bucket());
        asset.setContentType(command.contentType());
        asset.setOriginalFilename(command.originalFilename());
        asset.setSizeBytes(command.sizeBytes());
        asset.setAssetType(EvidenceAssetType.fromContentType(command.contentType()));
        asset.setSourceType(command.sourceType());
        asset.setReviewStatus(EvidenceReviewStatus.PENDING);
        asset.setTaskCode(command.taskCode());
        asset.setAlarmEventCode(command.alarmEventCode());
        asset.setDeviceCode(command.deviceCode());
        asset.setAnalysisId(command.analysisId());
        asset.setUploadedBy(
                command.uploadedBy() == null ? "system" : command.uploadedBy()
        );
        asset.setUploadedByName(
                command.uploadedByName() == null
                        ? "system"
                        : command.uploadedByName()
        );
        asset.setDerivativeStatus(EvidenceDerivativeStatus.PENDING);
        repository.save(asset);
        derivativeJobService.start(asset.getEvidenceCode());
        return asset;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String nextEvidenceCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
        return "EV-" + day + "-" + suffix;
    }
}
