package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EvidenceAssetRepository
        extends JpaRepository<EvidenceAsset, Long>,
                JpaSpecificationExecutor<EvidenceAsset> {

    Optional<EvidenceAsset> findByEvidenceCode(String evidenceCode);

    Optional<EvidenceAsset> findByObjectKey(String objectKey);

    List<EvidenceAsset> findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(
            String taskCode
    );

    List<EvidenceAsset> findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
            String alarmEventCode
    );

    List<EvidenceAsset> findByTaskCodeAndDeletedFalseOrderByCreatedAtAsc(
            String taskCode
    );

    List<EvidenceAsset> findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtAsc(
            String alarmEventCode
    );

    List<EvidenceAsset> findByDeletedFalseAndContentHashIsNullOrderByCreatedAtAsc(
            Pageable pageable
    );

    List<EvidenceAsset>
            findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                    String taskCode,
                    String alarmEventCode
            );
}
