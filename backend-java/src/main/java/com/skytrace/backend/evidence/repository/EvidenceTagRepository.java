package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceTagRepository extends JpaRepository<EvidenceTag, Long> {
}
