package com.uav.backend.evidence.repository;

import com.uav.backend.evidence.domain.EvidenceAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EvidenceAssetRepository extends JpaRepository<EvidenceAsset, Long> {
    Optional<EvidenceAsset> findByObjectKey(String objectKey);
}
