package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceAssetRepository extends JpaRepository<EvidenceAsset, Long> {
    Optional<EvidenceAsset> findByObjectKey(String objectKey);

    List<EvidenceAsset> findByTaskCodeOrderByCreatedAtDesc(String taskCode);

    List<EvidenceAsset> findByAlarmEventCodeOrderByCreatedAtDesc(
            String alarmEventCode);

    List<EvidenceAsset> findByTaskCodeAndAlarmEventCodeOrderByCreatedAtDesc(
            String taskCode,
            String alarmEventCode);
}
