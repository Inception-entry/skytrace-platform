package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceAccessLogRepository
        extends JpaRepository<EvidenceAccessLog, Long> {
}